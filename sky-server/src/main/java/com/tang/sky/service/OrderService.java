package com.tang.sky.service;
import com.tang.sky.pojo.OrderModels.*;
import com.tang.sky.pojo.CatalogModels.PageView;
/** 用户订单操作从当前身份取归属；状态迁移由后端控制，AI不能绕过此边界。 */
public interface OrderService {
    View create(CreateInput input);View detail(long id);PageView<Order> history(int page,int size);
    void cancel(long id);void again(long id);void advance(long id,Status next);
}
