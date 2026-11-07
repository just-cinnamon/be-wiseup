package com.wiseup.api;

import com.wiseup.api.application.mapper.VideoMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VideoMapper helpers.
 */
class VideoMapperTest {

    @Test
    void formatDurationMinutes() {
        assertEquals("5min",    VideoMapper.formatDuration("0:05:00"));
        assertEquals("12min",   VideoMapper.formatDuration("12:34"));
        assertEquals("1min",    VideoMapper.formatDuration("0:45"));
    }

    @Test
    void formatDurationHours() {
        assertEquals("1h 05min", VideoMapper.formatDuration("1:05:20"));
    }

    @Test
    void formatDurationEmpty() {
        assertEquals("", VideoMapper.formatDuration(null));
        assertEquals("", VideoMapper.formatDuration(""));
    }

    @Test
    void formatCategoryTitleCase() {
        assertEquals("Action Movies", VideoMapper.formatCategory("action-movies"));
        assertEquals("Documentary",   VideoMapper.formatCategory("documentary"));
    }

    @Test
    void formatCategoryStripsDisambiguation() {
        assertEquals("Comedy", VideoMapper.formatCategory("comedy-1"));
    }

    @Test
    void formatCategoryNumericRange() {
        assertEquals("18-25", VideoMapper.formatCategory("18-25"));
    }

    @Test
    void formatViewsMillions() {
        assertEquals("1,2 mi", VideoMapper.formatViews(1_234_567));
    }

    @Test
    void formatViewsThousands() {
        assertEquals("5,4 mil", VideoMapper.formatViews(5_400));
    }

    @Test
    void formatViewsSmall() {
        assertEquals("999", VideoMapper.formatViews(999));
    }

    @Test
    void decodeHtmlEntities() {
        assertEquals("Tom & Jerry", VideoMapper.decodeHtml("Tom &amp; Jerry"));
        assertEquals("\"quoted\"",  VideoMapper.decodeHtml("&quot;quoted&quot;"));
    }
}
