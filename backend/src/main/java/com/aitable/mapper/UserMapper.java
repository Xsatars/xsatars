package com.aitable.mapper;

import com.aitable.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 用户表 Mapper。
 *
 * <p>基础的增删改查由 MyBatis-Plus 的 {@link BaseMapper} 提供，
 * 需要写复杂 SQL 时再在 resources/mapper/ 下加同名 XML。</p>
 */
public interface UserMapper extends BaseMapper<User> {
}
