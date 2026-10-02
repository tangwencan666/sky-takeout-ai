package com.tang.sky.controller;
import com.tang.sky.common.Result;
import com.tang.sky.pojo.CartModels.*;
import com.tang.sky.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
/** API只接受商品与数量，不接受身份；删除与减少也要由Service按归属过滤。 */
@RestController @RequestMapping("/user/cart")
public class CartController {
    private final CartService service;public CartController(CartService service){this.service=service;}
    @GetMapping public Result<View> view(){return Result.success(service.view());}
    @PostMapping public Result<Void> add(@Valid @RequestBody AddInput input){service.add(input);return Result.success(null);}
    @PatchMapping("/{id}/reduce") public Result<Void> reduce(@PathVariable long id){service.reduce(id);return Result.success(null);}
    @DeleteMapping("/{id}")public Result<Void> delete(@PathVariable long id){service.delete(id);return Result.success(null);}
    @DeleteMapping public Result<Void> clear(){service.clear();return Result.success(null);}
}
