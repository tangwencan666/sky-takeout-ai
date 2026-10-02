package com.tang.sky.service;
import com.tang.sky.pojo.SetmealModels.*;
import com.tang.sky.pojo.CatalogModels.PageView;
/** 套餐组合规则与状态校验集中复用，购物车和下单阶段通过此接口核价。 */
public interface SetmealService {
    long create(Input input); void update(long id,Input input); void delete(long id); void status(long id,int status);
    View detail(long id,boolean available); PageView<Setmeal> page(Long categoryId,boolean available,int page,int size);
}
