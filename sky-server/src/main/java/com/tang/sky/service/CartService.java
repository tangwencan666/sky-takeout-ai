package com.tang.sky.service;
import com.tang.sky.pojo.CartModels.*;
/** 当前用户来自UserContext，不提供可由前端或AI选择的userId参数。 */
public interface CartService {
    void add(AddInput input); void reduce(long id);void delete(long id);void clear();View view();Product product(String type,long id);
}
