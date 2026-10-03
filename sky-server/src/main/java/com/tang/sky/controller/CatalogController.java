package com.tang.sky.controller;

import com.tang.sky.common.Result;
import com.tang.sky.pojo.CatalogModels.*;
import com.tang.sky.service.DishService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** 用户只读入口强制可售过滤；后台写操作由拦截器要求管理员身份。 */
@RestController
public class CatalogController {
    private final DishService service;
    public CatalogController(DishService service) { this.service=service; }
    @GetMapping("/user/categories") public Result<List<Category>> categories() { return Result.success(service.categories().stream().filter(c->c.status()==1).toList()); }
    @GetMapping("/admin/categories") public Result<List<Category>> adminCategories() { return Result.success(service.categories()); }
    @GetMapping("/admin/categories/page") public Result<PageView<Category>> categoryPage(@RequestParam(required=false) Integer type,@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return Result.success(service.categoryPage(type,keyword,page,size)); }
    @PutMapping("/admin/categories/{id}/status") public Result<Void> categoryStatus(@PathVariable long id,@RequestParam int status) { service.categoryStatus(id,status);return Result.success(null); }
    @PostMapping("/admin/categories") public Result<Long> category(@Valid @RequestBody CategoryInput input) { return Result.success(service.createCategory(input)); }
    @PutMapping("/admin/categories/{id}") public Result<Void> category(@PathVariable long id,@Valid @RequestBody CategoryInput input) { service.updateCategory(id,input); return Result.success(null); }
    @DeleteMapping("/admin/categories/{id}") public Result<Void> deleteCategory(@PathVariable long id) { service.deleteCategory(id); return Result.success(null); }
    @GetMapping("/user/dishes") public Result<PageView<Dish>> userPage(@RequestParam(required=false) Long categoryId,@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return Result.success(service.page(categoryId,true,keyword,page,size)); }
    @GetMapping("/admin/dishes") public Result<PageView<Dish>> adminPage(@RequestParam(required=false) Long categoryId,@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return Result.success(service.page(categoryId,false,keyword,page,size)); }
    @GetMapping("/user/dishes/{id}") public Result<DishView> userDetail(@PathVariable long id) { return Result.success(service.detail(id,true)); }
    @GetMapping("/admin/dishes/{id}") public Result<DishView> adminDetail(@PathVariable long id) { return Result.success(service.detail(id,false)); }
    @PostMapping("/admin/dishes") public Result<Long> create(@Valid @RequestBody DishInput input) { return Result.success(service.create(input)); }
    @PutMapping("/admin/dishes/{id}") public Result<Void> update(@PathVariable long id,@Valid @RequestBody DishInput input) { service.update(id,input); return Result.success(null); }
    @PutMapping("/admin/dishes/{id}/status") public Result<Void> status(@PathVariable long id,@RequestParam int status) { service.status(id,status); return Result.success(null); }
    @DeleteMapping("/admin/dishes/{id}") public Result<Void> delete(@PathVariable long id) { service.delete(id); return Result.success(null); }
}
