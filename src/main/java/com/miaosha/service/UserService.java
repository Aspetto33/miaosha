package com.miaosha.service;

import com.miaosha.error.BusinessException;
import com.miaosha.service.model.UserModel;
import org.springframework.stereotype.Service;


public interface UserService {

    //通过用户ID获取用户对象的方法
    UserModel getUserById(Integer id);

    //用户注册
    void register(UserModel userModel) throws BusinessException;

    //用户登录
    /*
    * telephone:用户注册手机号
    * password:用户加密后的密码
    * */
    UserModel validateLogin(String telephone,String encrptPassword) throws BusinessException;
}
