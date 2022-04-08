package com.miaosha.service.impl;

import com.miaosha.dao.OrderDOMapper;
import com.miaosha.dao.SequenceDOMapper;
import com.miaosha.dataobject.OrderDO;
import com.miaosha.dataobject.SequenceDO;
import com.miaosha.error.BusinessException;
import com.miaosha.error.EnumBusinessError;
import com.miaosha.service.ItemService;
import com.miaosha.service.OrderService;
import com.miaosha.service.UserService;
import com.miaosha.service.model.ItemModel;
import com.miaosha.service.model.OrderModel;
import com.miaosha.service.model.UserModel;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Service
public class OrderServiceImpl implements OrderService {

    @Resource
    private ItemService itemService;

    @Resource
    private UserService userService;

    @Resource
    private OrderDOMapper orderDOMapper;

    @Resource
    private SequenceDOMapper sequenceDOMapper;

    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount) throws BusinessException {
        //1.校验下单状态，下单的商品是否存在，用户是否合法，购买数量是否正确
        ItemModel itemById = itemService.getItemById(itemId);
        if(itemById == null){
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"商品信息不存在");
        }

        UserModel userById = userService.getUserById(userId);
        if(userById == null){
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"用户信息不存在");
        }

        if(amount <= 0 || amount > 99){
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"数量信息不正确");
        }

        //校验活动信息
        if(promoId != null){
            //(1)校验对应活动是否存在这个适用商品
            if(promoId.intValue() != itemById.getPromoModel().getId()){
                throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"活动信息不正确");
            }

            //(2)校验活动是否正在进行中
            else if(itemById.getPromoModel().getStatus().intValue() != 2){
                throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"活动还未开始");
            }

        }
        //2.落单减库存
        boolean result = itemService.decreaseStock(itemId,amount);
        if(result){
            throw new BusinessException(EnumBusinessError.STOCK_NOT_ENOUGH);
        }

        //3.订单入库
        OrderModel orderModel = new OrderModel();

        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);

        //如果商品活动存在，则商品价格为活动价格
        if(promoId != null){
            orderModel.setItemPrice(itemById.getPromoModel().getPromoItemPrice());
        }
        //否则，商品价格为平销价格
        else{
            orderModel.setItemPrice(itemById.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(new BigDecimal(amount)));

        //生成交易流水号，订单号
        orderModel.setId(generateOrderNo());
        OrderDO orderDO = convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);

        //加上商品的销量
        itemService.increaseSales(itemId,amount);

        //4.返回前端
        return orderModel;
    }

    //即使其他方法事务失败回滚，调用该方法仍然会成功执行
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String generateOrderNo(){
        //订单号有16位
        StringBuilder stringBuilder = new StringBuilder();

        //前8位为时间信息，年月日
        LocalDateTime now = LocalDateTime.now();
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-","");
        stringBuilder.append(nowDate);

        //中间6位为自增序列
        //获取当前sequence
        int sequence = 0;
        SequenceDO order_info = sequenceDOMapper.getSequenceByName("order_info");
        sequence = order_info.getCurrentValue();
        order_info.setCurrentValue(order_info.getCurrentValue()+order_info.getStep());
        sequenceDOMapper.updateByPrimaryKeySelective(order_info);
        String sequenceStr = String.valueOf(sequence);
        for(int i = 0;i < 6-sequenceStr.length();i++){
            stringBuilder.append(0);
        }
        stringBuilder.append(sequenceStr);

        //最后2位为分库分表位
        stringBuilder.append("00");

        return stringBuilder.toString();
    }

    private OrderDO convertFromOrderModel(OrderModel orderModel){
        if(orderModel == null){
            return null;
        }

        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel,orderDO);
        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDO.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        return orderDO;
    }
}
