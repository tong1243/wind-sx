package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.Rotation;

import java.util.List;

public interface RotationService extends IService<Rotation> {

    public List<Rotation> getAllRotation();
}
