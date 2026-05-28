package com.wut.screencommonsx.Response.Personal;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class UserNoticeResp {
    @Data
    public static class PageData<T> {
        private long pageNo;
        private long pageSize;
        private long total;
        private List<T> records;
    }

    @Data
    public static class NoticeRow {
        private Long id;
        private String noticeType;
        private String title;
        private String content;
        private Integer isRead;
        private Long relatedId;
        private LocalDateTime createTime;
        private LocalDateTime readTime;
    }

    @Data
    public static class NoticeListData {
        private long unreadCount;
        private PageData<NoticeRow> page;
    }
}
