package com.college.internship.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 数据传输对象 (Data Transfer Object) 标记基类
 */
@Data
public abstract class BaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;
}
