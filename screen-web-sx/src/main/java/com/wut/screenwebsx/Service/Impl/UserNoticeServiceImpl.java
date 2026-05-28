package com.wut.screenwebsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wut.screencommonsx.Model.UserNotice;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screencommonsx.Response.Personal.UserNoticeResp;
import com.wut.screenwebsx.Mapper.UserNoticeMapper;
import com.wut.screenwebsx.Service.UserNoticeService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserNoticeServiceImpl implements UserNoticeService {
    private final UserNoticeMapper userNoticeMapper;
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initNoticeTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_notice (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID',
                  user_phone VARCHAR(32) NOT NULL COMMENT '接收用户手机号',
                  notice_type VARCHAR(64) NOT NULL COMMENT '通知类型',
                  title VARCHAR(128) NOT NULL COMMENT '通知标题',
                  content TEXT NOT NULL COMMENT '通知内容',
                  is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
                  related_id BIGINT NULL COMMENT '关联业务ID（如车辆ID）',
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  read_time DATETIME NULL COMMENT '已读时间',
                  PRIMARY KEY (id),
                  KEY idx_user_notice_phone_read_time (user_phone, is_read, create_time),
                  KEY idx_user_notice_phone_time (user_phone, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息通知表'
                """);

        // 兼容历史表结构：旧库中 notice_type 可能是 ENUM 或较短 VARCHAR，导致新类型值写入被截断。
        jdbcTemplate.execute("""
                ALTER TABLE user_notice
                MODIFY COLUMN notice_type VARCHAR(64) NOT NULL COMMENT '通知类型'
                """);
        jdbcTemplate.execute("""
                ALTER TABLE user_notice
                MODIFY COLUMN title VARCHAR(128) NOT NULL COMMENT '通知标题'
                """);
        jdbcTemplate.execute("""
                ALTER TABLE user_notice
                MODIFY COLUMN content TEXT NOT NULL COMMENT '通知内容'
                """);
        jdbcTemplate.execute("""
                ALTER TABLE user_notice
                MODIFY COLUMN related_id BIGINT NULL COMMENT '关联业务ID（如车辆ID）'
                """);
        jdbcTemplate.execute("""
                ALTER TABLE user_notice
                MODIFY COLUMN is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读 1-已读'
                """);
    }

    @Override
    public ApiResponse<?> listMyNotices(String phone, long pageNo, long pageSize, Integer isRead) {
        long safePageNo = safePageNo(pageNo);
        long safePageSize = safePageSize(pageSize);

        LambdaQueryWrapper<UserNotice> wrapper = new LambdaQueryWrapper<UserNotice>()
                .eq(UserNotice::getUserPhone, phone)
                .eq(isRead != null, UserNotice::getIsRead, normalizeReadFlag(isRead))
                .orderByDesc(UserNotice::getCreateTime);

        Page<UserNotice> page = userNoticeMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        long unreadCount = userNoticeMapper.selectCount(new LambdaQueryWrapper<UserNotice>()
                .eq(UserNotice::getUserPhone, phone)
                .eq(UserNotice::getIsRead, 0));

        UserNoticeResp.NoticeListData data = new UserNoticeResp.NoticeListData();
        data.setUnreadCount(unreadCount);

        UserNoticeResp.PageData<UserNoticeResp.NoticeRow> pageData = new UserNoticeResp.PageData<>();
        pageData.setPageNo(page.getCurrent());
        pageData.setPageSize(page.getSize());
        pageData.setTotal(page.getTotal());
        pageData.setRecords(toRows(page.getRecords()));
        data.setPage(pageData);
        return ApiResponse.success("消息通知列表查询成功", data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> markNoticeRead(String phone, Long noticeId) {
        if (noticeId == null || noticeId <= 0) {
            return ApiResponse.badRequest("通知ID不合法");
        }
        int updated = userNoticeMapper.update(null, new LambdaUpdateWrapper<UserNotice>()
                .eq(UserNotice::getId, noticeId)
                .eq(UserNotice::getUserPhone, phone)
                .eq(UserNotice::getIsRead, 0)
                .set(UserNotice::getIsRead, 1)
                .set(UserNotice::getReadTime, LocalDateTime.now()));
        if (updated <= 0) {
            return ApiResponse.notFound("未找到对应通知或通知已读");
        }
        return ApiResponse.success("消息已标记为已读", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> markAllNoticeRead(String phone) {
        userNoticeMapper.update(null, new LambdaUpdateWrapper<UserNotice>()
                .eq(UserNotice::getUserPhone, phone)
                .eq(UserNotice::getIsRead, 0)
                .set(UserNotice::getIsRead, 1)
                .set(UserNotice::getReadTime, LocalDateTime.now()));
        return ApiResponse.success("全部消息已标记为已读", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> deleteNotice(String phone, Long noticeId) {
        if (noticeId == null || noticeId <= 0) {
            return ApiResponse.badRequest("通知ID不合法");
        }
        int deleted = userNoticeMapper.delete(new LambdaQueryWrapper<UserNotice>()
                .eq(UserNotice::getId, noticeId)
                .eq(UserNotice::getUserPhone, phone));
        if (deleted <= 0) {
            return ApiResponse.notFound("未找到对应通知");
        }
        return ApiResponse.success("消息删除成功", null);
    }

    private List<UserNoticeResp.NoticeRow> toRows(List<UserNotice> notices) {
        if (notices == null || notices.isEmpty()) {
            return Collections.emptyList();
        }
        return notices.stream().map(this::toRow).toList();
    }

    private UserNoticeResp.NoticeRow toRow(UserNotice notice) {
        UserNoticeResp.NoticeRow row = new UserNoticeResp.NoticeRow();
        row.setId(notice.getId());
        row.setNoticeType(notice.getNoticeType());
        row.setTitle(notice.getTitle());
        row.setContent(notice.getContent());
        row.setIsRead(notice.getIsRead());
        row.setRelatedId(notice.getRelatedId());
        row.setCreateTime(notice.getCreateTime());
        row.setReadTime(notice.getReadTime());
        return row;
    }

    private int normalizeReadFlag(Integer isRead) {
        if (isRead == null) {
            return 0;
        }
        return isRead == 1 ? 1 : 0;
    }

    private long safePageNo(long pageNo) {
        return pageNo <= 0 ? 1 : pageNo;
    }

    private long safePageSize(long pageSize) {
        if (pageSize <= 0) {
            return 10;
        }
        return Math.min(pageSize, 200);
    }
}
