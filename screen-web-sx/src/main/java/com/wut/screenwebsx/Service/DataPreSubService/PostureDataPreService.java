package com.wut.screenwebsx.Service.DataPreSubService;

import com.wut.screencommonsx.Model.TargetTimeModel;
import com.wut.screencommonsx.Response.Posture.PostureFlowTypeData;
import com.wut.screencommonsx.Util.CollectionEmptyUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screencommonsx.Util.MessagePrintUtil;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screendbmysqlsx.Model.Posture;
import com.wut.screendbmysqlsx.Service.PostureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static com.wut.screencommonsx.Static.WebModuleStatic.*;

@Component
public class PostureDataPreService {
    private final PostureService postureService;

    @Autowired
    public PostureDataPreService(PostureService postureService) {
        this.postureService = postureService;
    }

    public List<PostureFlowTypeData> initFlowTypeDataList() {
        return Stream.of(
                CAR_TYPE_COMPACT,
                CAR_TYPE_TRUCK,
                CAR_TYPE_BUS
        ).map(ModelTransformUtil::carTypeToPostureFlowTypeData).toList();
    }

    public Posture initRealTimePostureData(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        Posture posture = null;
        try {
            posture = postureService.getLatestOne(tableDateStr);
        } catch (Exception e) { MessagePrintUtil.printException(e, "initRealTimePostureData"); }
        return posture;
    }

    public List<Posture> initPeriodPostureData(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<Posture> postureList = new ArrayList<>();
        try {
            List<Posture> data = postureService.getListByDate(tableDateStr);
            if (!CollectionEmptyUtil.forList(data)) {
                postureList.addAll(data);
                postureList.sort(Comparator.comparingDouble(Posture::getTimestampStart));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initPeriodPostureData"); }
        return postureList;
    }

    public List<Posture> initTargetPostureData(TargetTimeModel targetTime) {
        List<Posture> postureList = new ArrayList<>();
        try {
            List<Posture> data = postureService.getListByTarget(targetTime.getTableDateStr(), targetTime.getStartTimestamp(), targetTime.getEndTimestamp());
            if (!CollectionEmptyUtil.forList(data)) {
                postureList.addAll(data);
                postureList.sort(Comparator.comparingDouble(Posture::getTimestampStart));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initTargetPostureData"); }
        return postureList;
    }

}
