package com.tang.sky.service.impl;
import com.tang.sky.common.*;
import com.tang.sky.mapper.*;
import com.tang.sky.pojo.SetmealModels.*;
import com.tang.sky.pojo.CatalogModels.PageView;
import com.tang.sky.service.*;
import java.util.HashSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 套餐主记录和组成必须同事务；可售套餐还要求所有组成菜品可售。 */
@Service
public class SetmealServiceImpl implements SetmealService {
    private final SetmealMapper mapper; private final CatalogMapper catalog; private final DishService dishes;
    public SetmealServiceImpl(SetmealMapper mapper,CatalogMapper catalog,DishService dishes) { this.mapper=mapper;this.catalog=catalog;this.dishes=dishes; }
    private void category(long id) { var c=catalog.category(id); if(c==null||c.type()!=2||c.status()!=1) throw new BusinessException(ErrorCode.BAD_REQUEST,"请选择有效套餐分类"); }
    private void validate(Input input,boolean available) {
        category(input.categoryId()); var ids=new HashSet<Long>();
        if(input.dishes().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST);
        for(var line:input.dishes()) { if(!ids.add(line.dishId())||line.quantity()<1) throw new BusinessException(ErrorCode.BAD_REQUEST,"套餐菜品重复或数量错误"); dishes.detail(line.dishId(),available); }
    }
    @Transactional public long create(Input input) { validate(input,false);mapper.insert(input);long id=mapper.lastId();for(var line:input.dishes())mapper.addLine(id,line);return id; }
    @Transactional public void update(long id,Input input) { var current=detail(id,false);validate(input,current.setmeal().status()==1);mapper.update(id,input);mapper.clearLines(id);for(var line:input.dishes())mapper.addLine(id,line); }
    public View detail(long id,boolean available) {
        var s=mapper.find(id); if(s==null)throw new BusinessException(ErrorCode.NOT_FOUND,"套餐不存在");
        var lines=mapper.lines(id);
        if(available) { if(s.status()!=1||lines.isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND,"套餐不可售");category(s.categoryId());for(var line:lines)dishes.detail(line.dishId(),true); }
        return new View(s,lines);
    }
    public PageView<Setmeal> page(Long categoryId,boolean available,int page,int size) { if(page<1||page>100000||size<1||size>100)throw new BusinessException(ErrorCode.BAD_REQUEST);return new PageView<>(mapper.count(categoryId,available),page,size,mapper.page(categoryId,available,(page-1)*size,size)); }
    @Transactional public void delete(long id) { if(detail(id,false).setmeal().status()==1)throw new BusinessException(ErrorCode.CONFLICT,"请先下架套餐");mapper.clearLines(id);mapper.delete(id); }
    @Transactional public void status(long id,int status) { if(status!=0&&status!=1)throw new BusinessException(ErrorCode.BAD_REQUEST);var s=detail(id,false);if(status==1){category(s.setmeal().categoryId());if(s.dishes().isEmpty())throw new BusinessException(ErrorCode.CONFLICT);for(var line:s.dishes())dishes.detail(line.dishId(),true);}mapper.status(id,status); }
}
