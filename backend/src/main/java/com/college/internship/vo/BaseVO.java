package com.college.internship.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 视图展示对象 (View Object) 标记基类
 */
@Data
public abstract class BaseVO implements Serializable {

    private static final long serialVersionUID = 1L;
}
