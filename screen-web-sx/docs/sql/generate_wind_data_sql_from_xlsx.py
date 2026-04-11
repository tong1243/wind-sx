#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
根据“事故全过程_前后总80h风速矩阵.xlsx”生成 wind_data 导入 SQL。

设计目标：
1. 不依赖第三方库（仅使用 Python 标准库）；
2. 兼容 xlsx 的 sharedStrings + sheet XML 解析；
3. 将每个 sheet 的“小时-桩号矩阵”展开为 wind_data 逐路段记录；
4. 提供可配置参数，便于后续重复生成。

默认假设（可通过参数覆盖）：
1. direction 固定为“哈密”；
2. 风向固定为“NW”；
3. 每个 sheet 使用不同时间基准，避免时间冲突；
4. 经纬度未知时填 0（表字段非空约束）。
"""

from __future__ import annotations

import argparse
import datetime as dt
import re
import zipfile
import xml.etree.ElementTree as et
from pathlib import Path
from typing import Dict, List, Optional, Tuple

NS = {
    "m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "p": "http://schemas.openxmlformats.org/package/2006/relationships",
}

HOUR_RE = re.compile(r"^\s*(-?\d+)\s*h\s*$", re.IGNORECASE)
STAKE_RE = re.compile(r"^K\d+(?:\+\d+)?$", re.IGNORECASE)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="从事故风速矩阵 xlsx 生成 wind_data SQL")
    parser.add_argument("--xlsx", required=True, help="xlsx 文件绝对路径")
    parser.add_argument("--out", required=True, help="输出 SQL 文件路径")
    parser.add_argument("--base-time", default="2026-01-01 00:00:00", help="第一张 sheet 的基准时间（yyyy-MM-dd HH:mm:ss）")
    parser.add_argument("--sheet-gap-hours", type=int, default=200, help="不同 sheet 之间的时间偏移小时")
    parser.add_argument("--direction", default="哈密", help="direction 字段写入值")
    parser.add_argument("--wind-direction", default="NW", help="wind_direction 字段写入值")
    parser.add_argument("--data-source-prefix", default="事故风速矩阵", help="data_source 前缀")
    parser.add_argument("--start-lng", type=float, default=0.0, help="起点经度默认值")
    parser.add_argument("--start-lat", type=float, default=0.0, help="起点纬度默认值")
    parser.add_argument("--end-lng", type=float, default=0.0, help="终点经度默认值")
    parser.add_argument("--end-lat", type=float, default=0.0, help="终点纬度默认值")
    return parser.parse_args()


def read_shared_strings(zf: zipfile.ZipFile) -> List[str]:
    if "xl/sharedStrings.xml" not in zf.namelist():
        return []
    root = et.fromstring(zf.read("xl/sharedStrings.xml"))
    values: List[str] = []
    for si in root.findall("m:si", NS):
        text_parts = [node.text or "" for node in si.findall(".//m:t", NS)]
        values.append("".join(text_parts))
    return values


def read_sheet_targets(zf: zipfile.ZipFile) -> List[Tuple[str, str]]:
    wb = et.fromstring(zf.read("xl/workbook.xml"))
    rels = et.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
    rid_to_target: Dict[str, str] = {}
    for rel in rels.findall("p:Relationship", NS):
        rid = rel.attrib["Id"]
        target = rel.attrib["Target"]
        if not target.startswith("xl/"):
            target = "xl/" + target
        rid_to_target[rid] = target

    sheets: List[Tuple[str, str]] = []
    for sheet in wb.findall("m:sheets/m:sheet", NS):
        name = sheet.attrib.get("name", "")
        rid = sheet.attrib.get("{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id", "")
        target = rid_to_target.get(rid, "")
        if name and target:
            sheets.append((name, target))
    return sheets


def excel_cell_value(cell: et.Element, shared: List[str]) -> str:
    t = cell.attrib.get("t")
    v = cell.find("m:v", NS)
    if v is None:
        inline_text = cell.find("m:is/m:t", NS)
        return "" if inline_text is None else (inline_text.text or "")
    raw = v.text or ""
    if t == "s":
        try:
            return shared[int(raw)]
        except Exception:
            return raw
    return raw


def parse_sheet_matrix(zf: zipfile.ZipFile, target: str, shared: List[str]) -> Tuple[List[str], List[Tuple[int, List[float]]]]:
    root = et.fromstring(zf.read(target))
    rows = root.findall("m:sheetData/m:row", NS)
    if not rows:
        return [], []

    # 第1行：桩号头（第1列为空，从第2列开始是 Kxxxx）
    header_cells = rows[0].findall("m:c", NS)
    stakes: List[str] = []
    for c in header_cells[1:]:
        value = excel_cell_value(c, shared).strip()
        if STAKE_RE.match(value):
            stakes.append(value.upper())
        else:
            stakes.append("")

    data_rows: List[Tuple[int, List[float]]] = []
    for row in rows[1:]:
        cells = row.findall("m:c", NS)
        if not cells:
            continue
        hour_text = excel_cell_value(cells[0], shared).strip()
        m = HOUR_RE.match(hour_text)
        if not m:
            continue
        hour = int(m.group(1))
        values: List[float] = []
        for c in cells[1:]:
            raw = excel_cell_value(c, shared).strip()
            if raw == "":
                values.append(float("nan"))
                continue
            try:
                values.append(float(raw))
            except ValueError:
                values.append(float("nan"))
        data_rows.append((hour, values))
    return stakes, data_rows


def wind_speed_to_level(speed: float) -> int:
    # 近似蒲福风级阈值（m/s）
    if speed >= 32.7:
        return 12
    if speed >= 28.5:
        return 11
    if speed >= 24.5:
        return 10
    if speed >= 20.8:
        return 9
    if speed >= 17.2:
        return 8
    if speed >= 13.9:
        return 7
    if speed >= 10.8:
        return 6
    if speed >= 8.0:
        return 5
    if speed >= 5.5:
        return 4
    if speed >= 3.4:
        return 3
    if speed >= 1.6:
        return 2
    return 1


def level_to_control(level: int) -> int:
    if level >= 12:
        return 1
    if level >= 11:
        return 2
    if level >= 9:
        return 3
    if level >= 7:
        return 4
    return 5


def control_to_limits(control_level: int) -> Tuple[int, int]:
    # 返回 (heavy, light)
    if control_level == 1:
        return 0, 0
    if control_level == 2:
        return 0, 60
    if control_level == 3:
        return 40, 60
    if control_level == 4:
        return 60, 80
    return 80, 120


def sql_escape(text: str) -> str:
    return text.replace("\\", "\\\\").replace("'", "''")


def main() -> None:
    args = parse_args()
    xlsx_path = Path(args.xlsx)
    out_path = Path(args.out)

    base_time = dt.datetime.strptime(args.base_time, "%Y-%m-%d %H:%M:%S")
    out_path.parent.mkdir(parents=True, exist_ok=True)

    insert_count = 0
    with zipfile.ZipFile(xlsx_path, "r") as zf, out_path.open("w", encoding="utf-8", newline="\n") as out:
        shared = read_shared_strings(zf)
        sheets = read_sheet_targets(zf)

        out.write("-- 由 generate_wind_data_sql_from_xlsx.py 自动生成\n")
        out.write(f"-- 源文件: {xlsx_path}\n")
        out.write(f"-- 生成时间: {dt.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
        out.write("SET NAMES utf8mb4;\n")
        out.write("START TRANSACTION;\n\n")

        for sheet_idx, (sheet_name, target) in enumerate(sheets, start=1):
            stakes, data_rows = parse_sheet_matrix(zf, target, shared)
            if len(stakes) < 2 or not data_rows:
                continue

            sheet_base = base_time + dt.timedelta(hours=(sheet_idx - 1) * args.sheet_gap_hours)
            out.write(f"-- ==================== {sheet_name} ====================\n")
            for hour, values in data_rows:
                current_time = sheet_base + dt.timedelta(hours=hour)
                time_text = current_time.strftime("%Y-%m-%d %H:%M:%S")
                limit = min(len(stakes), len(values))
                for i in range(limit - 1):
                    s1 = stakes[i]
                    s2 = stakes[i + 1]
                    v1 = values[i]
                    v2 = values[i + 1]
                    if not s1 or not s2:
                        continue
                    if v1 != v1 and v2 != v2:  # NaN 判断
                        continue

                    # 路段风速口径：取相邻两个桩号中的较大值（偏安全）
                    candidates = [v for v in (v1, v2) if v == v]
                    wind_speed = max(candidates)
                    wind_level = wind_speed_to_level(wind_speed)
                    control_level = level_to_control(wind_level)
                    heavy_limit, light_limit = control_to_limits(control_level)

                    data_source = f"{args.data_source_prefix}-{sheet_name}"
                    sql = (
                        "INSERT INTO wind_data "
                        "(time_stamp, direction, start_stake, end_stake, start_longitude, start_latitude, end_longitude, end_latitude, "
                        "wind_speed, wind_direction, heavy_vehicle_speed_limit, light_vehicle_speed_limit, control_level, data_source) "
                        f"VALUES ('{time_text}', '{sql_escape(args.direction)}', '{sql_escape(s1)}', '{sql_escape(s2)}', "
                        f"{args.start_lng:.6f}, {args.start_lat:.6f}, {args.end_lng:.6f}, {args.end_lat:.6f}, "
                        f"{wind_speed:.1f}, '{sql_escape(args.wind_direction)}', {heavy_limit}, {light_limit}, {control_level}, '{sql_escape(data_source)}');\n"
                    )
                    out.write(sql)
                    insert_count += 1
            out.write("\n")

        out.write("COMMIT;\n")

    print(f"done. output={out_path} rows={insert_count}")


if __name__ == "__main__":
    main()

