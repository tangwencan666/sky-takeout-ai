package com.tang.sky.service.impl;

import com.tang.sky.common.*;
import com.tang.sky.mapper.CatalogMapper;
import com.tang.sky.pojo.CatalogModels.*;
import com.tang.sky.service.DishService;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 菜品与口味同事务写入；数据库外键兜底引用完整性，失败时整体回滚。 */
@Service
public class DishServiceImpl implements DishService {
    private final CatalogMapper mapper;
    public DishServiceImpl(CatalogMapper mapper) { this.mapper=mapper; }
    public List<Category> categories() { return mapper.categories(); }
    @Transactional public long createCategory(CategoryInput input) { mapper.insertCategory(input); return mapper.lastId(); }
    @Transactional public void updateCategory(long id,CategoryInput input) {
        var current=mapper.category(id);
        if(current==null) throw new BusinessException(ErrorCode.NOT_FOUND);
        if(current.type()!=input.type()) throw new BusinessException(ErrorCode.CONFLICT,"分类类型建立后不可修改");
        mapper.updateCategory(id,input);
    }
    @Transactional public void deleteCategory(long id) {
        try { if(mapper.deleteCategory(id)==0) throw new BusinessException(ErrorCode.NOT_FOUND); }
        catch(DataIntegrityViolationException e) { throw new BusinessException(ErrorCode.CONFLICT,"分类仍被商品引用"); }
    }
    private void category(long id) {
        var c=mapper.category(id);
        if(c==null || c.type()!=1 || c.status()!=1) throw new BusinessException(ErrorCode.BAD_REQUEST,"请选择有效菜品分类");
    }
    public DishView detail(long id,boolean availableOnly) {
        var dish=mapper.dish(id);
        if(dish==null || (availableOnly && (dish.status()!=1 || mapper.category(dish.categoryId()).status()!=1))) throw new BusinessException(ErrorCode.NOT_FOUND,"菜品不存在或不可售");
        return new DishView(dish,mapper.flavors(id));
    }
    public PageView<Dish> page(Long categoryId,boolean availableOnly,String keyword,int page,int size) {
        if(page<1 || page>100000 || size<1 || size>100 || (keyword!=null && keyword.length()>100)) throw new BusinessException(ErrorCode.BAD_REQUEST);
        return new PageView<>(mapper.count(categoryId,availableOnly,keyword),page,size,mapper.page(categoryId,availableOnly,keyword,(page-1)*size,size));
    }
    @Transactional public long create(DishInput input) {
        category(input.categoryId()); mapper.insertDish(input); long id=mapper.lastId();
        for(var flavor:input.flavors()) mapper.insertFlavor(id,flavor);
        return id;
    }
    @Transactional public void update(long id,DishInput input) {
        detail(id,false); category(input.categoryId()); mapper.updateDish(id,input); mapper.deleteFlavors(id);
        for(var flavor:input.flavors()) mapper.insertFlavor(id,flavor);
    }
    @Transactional public void delete(long id) {
        if(detail(id,false).dish().status()==1) throw new BusinessException(ErrorCode.CONFLICT,"请先下架菜品");
        try { mapper.deleteFlavors(id); mapper.deleteDish(id); }
        catch(DataIntegrityViolationException e) { throw new BusinessException(ErrorCode.CONFLICT,"菜品仍被套餐引用"); }
    }
    @Transactional public void status(long id,int status) {
        if(status!=0 && status!=1) throw new BusinessException(ErrorCode.BAD_REQUEST);
        var dish=detail(id,false).dish(); if(status==1) category(dish.categoryId()); mapper.status(id,status);
    }
}
