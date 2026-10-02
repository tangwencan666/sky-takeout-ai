package com.tang.sky.controller;
import com.tang.sky.common.Result;
import com.tang.sky.pojo.SetmealModels.*;
import com.tang.sky.pojo.CatalogModels.PageView;
import com.tang.sky.service.SetmealService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
/** 管理端维护组合，用户端只能浏览真实可售套餐；所有业务判断交给Service。 */
@RestController
public class SetmealController {
    private final SetmealService service;
    public SetmealController(SetmealService service){this.service=service;}
    @GetMapping("/user/setmeals") public Result<PageView<Setmeal>> userPage(@RequestParam(required=false)Long categoryId,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return Result.success(service.page(categoryId,true,page,size));}
    @GetMapping("/admin/setmeals") public Result<PageView<Setmeal>> adminPage(@RequestParam(required=false)Long categoryId,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return Result.success(service.page(categoryId,false,page,size));}
    @GetMapping("/user/setmeals/{id}")public Result<View> userDetail(@PathVariable long id){return Result.success(service.detail(id,true));}
    @GetMapping("/admin/setmeals/{id}")public Result<View> adminDetail(@PathVariable long id){return Result.success(service.detail(id,false));}
    @PostMapping("/admin/setmeals")public Result<Long> create(@Valid @RequestBody Input input){return Result.success(service.create(input));}
    @PutMapping("/admin/setmeals/{id}")public Result<Void> update(@PathVariable long id,@Valid @RequestBody Input input){service.update(id,input);return Result.success(null);}
    @PutMapping("/admin/setmeals/{id}/status")public Result<Void> status(@PathVariable long id,@RequestParam int status){service.status(id,status);return Result.success(null);}
    @DeleteMapping("/admin/setmeals/{id}")public Result<Void> delete(@PathVariable long id){service.delete(id);return Result.success(null);}
}
