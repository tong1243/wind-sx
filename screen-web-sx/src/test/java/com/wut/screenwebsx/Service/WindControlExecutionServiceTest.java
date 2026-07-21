package com.wut.screenwebsx.Service;

import com.wut.screendbmysqlsx.Service.PublishFacilityStaticService;
import com.wut.screendbmysqlsx.Service.VmsContentTemplateStaticService;
import com.wut.screendbmysqlsx.Service.WindDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WindControlExecutionServiceTest {
    @Mock
    private WindControlStateService stateService;
    @Mock
    private WindDataService windDataService;
    @Mock
    private WindControlWindImpactService windImpactService;
    @Mock
    private PublishFacilityStaticService publishFacilityStaticService;
    @Mock
    private VmsContentTemplateStaticService vmsContentTemplateStaticService;
    @Mock
    private WindControlResourceService resourceService;
    @Mock
    private WindControlPersistenceService persistenceService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private final Map<String, Map<String, Object>> generatedPlans = new LinkedHashMap<>();
    private final List<Map<String, Object>> windEventRecords = new ArrayList<>();
    private final Map<String, Integer> currentControlLevelBySegment = new LinkedHashMap<>();
    private final Map<Integer, Map<String, Object>> controlPlanLibrary = new LinkedHashMap<>();
    private WindControlExecutionService executionService;

    @BeforeEach
    void setUp() {
        controlPlanLibrary.put(4, planTemplate(4, "蓝色警戒"));
        when(stateService.getGeneratedPlans()).thenReturn(generatedPlans);
        lenient().when(stateService.getWindEventRecords()).thenReturn(windEventRecords);
        lenient().when(stateService.getCurrentControlLevelBySegment()).thenReturn(currentControlLevelBySegment);
        lenient().when(stateService.getPersistenceService()).thenReturn(persistenceService);
        lenient().when(stateService.getDefaultControlLevel()).thenReturn(5);
        lenient().when(stateService.getStaffList()).thenReturn(List.of());
        lenient().when(stateService.getDutyTeams()).thenReturn(List.of());
        lenient().when(stateService.getPublishFacilities()).thenReturn(List.of());
        lenient().when(stateService.getControlPlanLibrary()).thenReturn(controlPlanLibrary);
        lenient().when(stateService.getSpeedThresholdByWindLevel()).thenReturn(Map.of());
        lenient().when(stateService.mapWindToControlLevel(anyInt())).thenReturn(4);
        lenient().when(publishFacilityStaticService.getEnabledFacilities()).thenReturn(List.of());
        when(stateService.stringValue(any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            return value == null ? "" : String.valueOf(value);
        });
        lenient().when(stateService.intValue(any(), anyInt())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            int defaultValue = invocation.getArgument(1);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value == null) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (Exception ignored) {
                return defaultValue;
            }
        });

        executionService = new WindControlExecutionService(
                stateService,
                windDataService,
                windImpactService,
                publishFacilityStaticService,
                vmsContentTemplateStaticService,
                resourceService,
                jdbcTemplate
        );
    }

    @Test
    void buildExecutionTableDoesNotPublishOrCreateSideEffects() {
        String planId = "abcd1234";
        generatedPlans.put(planId, draftPlan(planId));

        Map<String, Object> result = executionService.buildExecutionTableByEdit(planId);

        assertEquals("DRAFT", result.get("status"));
        assertEquals(false, result.get("executionApplied"));
        assertFalse(result.containsKey("eventId"));
        assertFalse(result.containsKey("dispatchRecordId"));
        assertTrue(windEventRecords.isEmpty());
        assertEquals("DRAFT", generatedPlans.get(planId).get("status"));
        assertNull(generatedPlans.get(planId).get("eventId"));
        assertNull(generatedPlans.get(planId).get("dispatchRecordId"));
        assertEquals(5, generatedPlans.get(planId).get("controlLevel"));
        assertEquals("正常通行", generatedPlans.get(planId).get("controlLevelText"));
        assertEquals(4, generatedPlans.get(planId).get("recommendedControlLevel"));
        assertEquals("蓝色警戒", generatedPlans.get(planId).get("recommendedControlLevelText"));
        verify(resourceService, never()).createDispatchRecord(any());
        verify(resourceService, never()).findLatestDispatchRecordByPlanId(any());
        verify(persistenceService, never()).upsertEvent(any());
    }

    @Test
    void publishPlanOnlyPublishesPlan() {
        String planId = "abcd1234";
        generatedPlans.put(planId, draftPlan(planId));

        Map<String, Object> result = executionService.publishPlan(planId);

        assertEquals("PUBLISHED", result.get("status"));
        assertEquals(true, result.get("executionApplied"));
        assertEquals(4, result.get("controlLevel"));
        assertEquals(4, result.get("currentControlLevel"));
        assertEquals("蓝色警戒", result.get("controlLevelText"));
        assertEquals("蓝色警戒", result.get("currentControlLevelText"));
        assertNull(result.get("eventId"));
        assertNull(result.get("dispatchRecordId"));
        assertTrue(windEventRecords.isEmpty());
        verify(resourceService, never()).findLatestDispatchRecordByPlanId(any());
        verify(resourceService, never()).createDispatchRecord(any());
        verify(persistenceService, never()).upsertEvent(any());
        verify(persistenceService).upsertPlan(any());
        verify(stateService).persistSnapshot();
    }

    @Test
    void createDispatchRecordForPublishedPlanCreatesRecord() {
        String planId = "abcd1234";
        Map<String, Object> plan = draftPlan(planId);
        plan.put("status", "PUBLISHED");
        generatedPlans.put(planId, plan);
        when(resourceService.createDispatchRecord(any())).thenAnswer(invocation -> {
            Map<String, Object> body = new LinkedHashMap<>(invocation.getArgument(0));
            body.put("recordId", "DISP-001");
            return body;
        });

        Map<String, Object> result = executionService.createDispatchRecordForPlan(planId);

        assertEquals("DISP-001", result.get("recordId"));
        assertEquals("DISP-001", generatedPlans.get(planId).get("dispatchRecordId"));
        ArgumentCaptor<Map<String, Object>> dispatchCaptor = ArgumentCaptor.forClass(Map.class);
        verify(resourceService).createDispatchRecord(dispatchCaptor.capture());
        assertEquals(planId, dispatchCaptor.getValue().get("planId"));
        assertEquals("一中队", dispatchCaptor.getValue().get("team"));
        assertEquals("限速", dispatchCaptor.getValue().get("dispatchReason"));
        verify(resourceService, never()).findLatestDispatchRecordByPlanId(any());
        verify(persistenceService).upsertPlan(any());
        verify(stateService).persistSnapshot();
    }

    @Test
    void createRunningEventReportForPublishedPlanCreatesRunningEvent() {
        String planId = "abcd1234";
        Map<String, Object> plan = draftPlan(planId);
        plan.put("status", "PUBLISHED");
        generatedPlans.put(planId, plan);

        Map<String, Object> result = executionService.createRunningEventReportForPlan(planId);

        assertEquals("RUNNING", result.get("status"));
        assertFalse(String.valueOf(result.get("eventId")).isBlank());
        assertEquals(result.get("eventId"), generatedPlans.get(planId).get("eventId"));
        assertEquals(1, windEventRecords.size());
        assertEquals(result.get("eventId"), windEventRecords.get(0).get("eventId"));
        verify(persistenceService).upsertEvent(any());
        verify(persistenceService).upsertPlan(any());
        verify(stateService).persistSnapshot();
    }

    @Test
    void createEndpointsCreateMultipleRecordsForSamePlan() {
        String planId = "abcd1234";
        Map<String, Object> plan = draftPlan(planId);
        plan.put("status", "PUBLISHED");
        generatedPlans.put(planId, plan);
        final int[] dispatchSeq = {0};
        when(resourceService.createDispatchRecord(any())).thenAnswer(invocation -> {
            Map<String, Object> body = new LinkedHashMap<>(invocation.getArgument(0));
            body.put("recordId", "DISP-00" + (++dispatchSeq[0]));
            return body;
        });

        Map<String, Object> firstEvent = executionService.createRunningEventReportForPlan(planId);
        Map<String, Object> secondEvent = executionService.createRunningEventReportForPlan(planId);
        Map<String, Object> firstDispatch = executionService.createDispatchRecordForPlan(planId);
        Map<String, Object> secondDispatch = executionService.createDispatchRecordForPlan(planId);

        assertNotEquals(firstEvent.get("eventId"), secondEvent.get("eventId"));
        assertEquals(2, windEventRecords.size());
        assertEquals(planId, windEventRecords.get(0).get("planId"));
        assertEquals(planId, windEventRecords.get(1).get("planId"));
        assertEquals("DISP-001", firstDispatch.get("recordId"));
        assertEquals("DISP-002", secondDispatch.get("recordId"));
        assertEquals("DISP-002", generatedPlans.get(planId).get("dispatchRecordId"));
        verify(resourceService, times(2)).createDispatchRecord(any());
        verify(resourceService, never()).findLatestDispatchRecordByPlanId(any());
        verify(persistenceService, times(2)).upsertEvent(any());
    }

    @Test
    void closePlanDoesNotCreateEventReportWhenNoneExists() {
        String planId = "abcd1234";
        Map<String, Object> plan = draftPlan(planId);
        plan.put("status", "PUBLISHED");
        generatedPlans.put(planId, plan);

        Map<String, Object> result = executionService.closePlan(planId);

        assertEquals("CLOSED", result.get("status"));
        assertTrue(windEventRecords.isEmpty());
        verify(persistenceService, never()).upsertEvent(any());
        verify(persistenceService).upsertPlan(any());
        verify(stateService).persistSnapshot();
    }

    @Test
    void publishedPlanCannotBePublishedAgain() {
        String planId = "abcd1234";
        Map<String, Object> plan = draftPlan(planId);
        plan.put("status", "PUBLISHED");
        generatedPlans.put(planId, plan);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executionService.publishPlan(planId));

        assertTrue(ex.getMessage().contains("plan already published"));
        verify(resourceService, never()).createDispatchRecord(any());
        verify(persistenceService, never()).upsertEvent(any());
    }

    private Map<String, Object> draftPlan(String planId) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planId", planId);
        plan.put("status", "DRAFT");
        plan.put("timestamp", 1_788_000_000_000L);
        plan.put("segment", "K3192-K3197");
        plan.put("segmentText", "K3192-K3197");
        plan.put("startStake", "K3192");
        plan.put("endStake", "K3197");
        plan.put("direction", 1);
        plan.put("recommendedControlLevel", 4);
        plan.put("recommendedControlLevelText", "蓝色警戒");
        plan.put("currentControlLevel", 5);
        plan.put("currentControlLevelText", "正常通行");
        plan.put("controlLevel", 5);
        plan.put("controlLevelText", "正常通行");
        plan.put("realtimeWindLevel", 8);
        plan.put("forecastMaxWindLevel", 9);
        plan.put("managementPlan", "限速");
        plan.put("teamId", "TEAM-1");
        plan.put("team", "一中队");
        return plan;
    }

    private Map<String, Object> planTemplate(int level, String levelName) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("level", level);
        template.put("levelName", levelName);
        template.put("riskSectionPlan", "区段限速");
        template.put("upstreamExitPlan", "出口限速");
        template.put("upstreamEntryPlan", "入口限速");
        template.put("upstreamServiceAreaPlan", "服务区限速");
        return template;
    }
}
