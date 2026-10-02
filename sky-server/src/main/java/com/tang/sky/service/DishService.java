package com.tang.sky.service;

import com.tang.sky.pojo.CatalogModels.*;
import java.util.List;

/** 普通业务与未来AI工具共享此边界，上架、分类和事务规则不交给模型决定。 */
public interface DishService {
    List<Category> categories();
    long createCategory(CategoryInput input);
    void updateCategory(long id,CategoryInput input);
    void deleteCategory(long id);
    DishView detail(long id,boolean availableOnly);
    PageView<Dish> page(Long categoryId,boolean availableOnly,String keyword,int page,int size);
    long create(DishInput input);
    void update(long id,DishInput input);
    void delete(long id);
    void status(long id,int status);
}
