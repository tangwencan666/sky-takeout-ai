package com.tang.sky.mapper;

import com.tang.sky.pojo.AuthModels.Account;
import org.apache.ibatis.annotations.*;

/** 表名固定、参数绑定，普通用户与管理员查询明确分开，不允许请求动态拼接表名。 */
@Mapper
public interface AccountMapper {
    @Select("SELECT id,username,password_hash AS passwordHash,status FROM `user` WHERE username=#{name}")
    Account findUser(String name);
    @Select("SELECT id,username,password_hash AS passwordHash,status FROM employee WHERE username=#{name}")
    Account findEmployee(String name);
    @Select("SELECT id,username,password_hash AS passwordHash,status FROM `user` WHERE id=#{id}")
    Account userById(long id);
    @Select("SELECT id,username,password_hash AS passwordHash,status FROM employee WHERE id=#{id}")
    Account employeeById(long id);
    @Insert("INSERT INTO `user`(username,password_hash) VALUES(#{name},#{hash})")
    int insertUser(@Param("name") String name, @Param("hash") String hash);
}
