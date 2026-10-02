package com.tang.sky.service.impl;
import com.tang.sky.common.*;
import com.tang.sky.mapper.CartMapper;
import com.tang.sky.pojo.CartModels.*;
import com.tang.sky.service.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 对同一用户加行锁串行化购物车修改；金额按当前可售商品重算，不保存客户端价格。 */
@Service
public class CartServiceImpl implements CartService {
    private final CartMapper mapper;private final DishService dishes;private final SetmealService meals;
    public CartServiceImpl(CartMapper mapper,DishService dishes,SetmealService meals){this.mapper=mapper;this.dishes=dishes;this.meals=meals;}
    private long lock(){long id=UserContext.userId();if(mapper.lockUser(id)==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);return id;}
    public Product product(String type,long id){
        if(type.equals("DISH")){var d=dishes.detail(id,true).dish();return new Product(d.name(),d.price());}
        if(type.equals("SETMEAL")){var m=meals.detail(id,true).setmeal();return new Product(m.name(),m.price());}
        throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
    @Transactional public void add(AddInput input){
        long user=lock();product(input.itemType(),input.itemId());
        if(input.quantity()<1||input.quantity()>99)throw new BusinessException(ErrorCode.BAD_REQUEST);
        var same=mapper.same(user,input);int quantity=input.quantity()+(same==null?0:same.quantity());
        if(quantity>99)throw new BusinessException(ErrorCode.CONFLICT,"单项数量最多99份");
        if(same==null)mapper.insert(user,input);else mapper.quantity(same.id(),user,quantity);
    }
    @Transactional public void reduce(long id){long user=lock();var item=mapper.find(id,user);if(item==null)throw new BusinessException(ErrorCode.NOT_FOUND);if(item.quantity()==1)mapper.delete(id,user);else mapper.quantity(id,user,item.quantity()-1);}
    @Transactional public void delete(long id){long user=lock();if(mapper.delete(id,user)==0)throw new BusinessException(ErrorCode.NOT_FOUND);}
    @Transactional public void clear(){mapper.clear(lock());}
    public View view(){
        var lines=new ArrayList<Line>();BigDecimal total=BigDecimal.ZERO;boolean available=true;
        for(var item:mapper.list(UserContext.userId())){
            try{var p=product(item.itemType(),item.itemId());var subtotal=p.price().multiply(BigDecimal.valueOf(item.quantity()));total=total.add(subtotal);lines.add(new Line(item.id(),item.itemType(),item.itemId(),item.flavor(),item.quantity(),p.name(),p.price(),subtotal,true));}
            catch(BusinessException e){if(e.error()!=ErrorCode.NOT_FOUND&&e.error()!=ErrorCode.BAD_REQUEST)throw e;available=false;lines.add(new Line(item.id(),item.itemType(),item.itemId(),item.flavor(),item.quantity(),"商品暂不可售",null,null,false));}
        }
        return new View(lines,total,available&&!lines.isEmpty());
    }
}
