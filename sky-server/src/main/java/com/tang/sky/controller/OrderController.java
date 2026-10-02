package com.tang.sky.controller;
import com.tang.sky.common.Result;
import com.tang.sky.pojo.OrderModels.*;
import com.tang.sky.pojo.CatalogModels.PageView;
import com.tang.sky.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
/** 用户操作由归属校验保护，管理员状态接口还在Service中二次校验角色。 */
@RestController
public class OrderController {
    private final OrderService service;public OrderController(OrderService service){this.service=service;}
    @PostMapping("/user/orders")public Result<View> create(@Valid @RequestBody CreateInput input){return Result.success(service.create(input));}
    @GetMapping("/user/orders")public Result<PageView<Order>> history(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return Result.success(service.history(page,size));}
    @GetMapping("/user/orders/{id}")public Result<View> detail(@PathVariable long id){return Result.success(service.detail(id));}
    @PostMapping("/user/orders/{id}/cancel")public Result<Void> cancel(@PathVariable long id){service.cancel(id);return Result.success(null);}
    @PostMapping("/user/orders/{id}/again")public Result<Void> again(@PathVariable long id){service.again(id);return Result.success(null);}
    @PutMapping("/admin/orders/{id}/status")public Result<Void> advance(@PathVariable long id,@RequestParam Status status){service.advance(id,status);return Result.success(null);}
}
