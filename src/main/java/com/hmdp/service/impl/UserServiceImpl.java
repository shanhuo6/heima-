package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate  stringRedisTemplate;


    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验，调用utils包下的工具类来检验是否合法
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //生成验证码,hutol工具
        String code = RandomUtil.randomNumbers(6);
/*        保存验证码到session
        session.setAttribute("code",code);*/
        //保存验证码在redis里,key加前缀避免多个业务的覆盖，因为redis是共享数据库,设置验证码过期时间，过期直接清除
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY +phone,code, LOGIN_CODE_TTL , TimeUnit.MINUTES);
        //发送验证码
        log.debug("发送验证码成功{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //校验验证码->redis取出code
/*        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();*/
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        //不一致，报错
        if(cacheCode == null || !cacheCode.toString().equals(code)) {
            return Result.fail("验证码错误");
        }


        //一致，查询用户
        User user = query().eq("phone",phone).one();
        //不存在，创建新用户
        if(user == null) {
            user = createUserWithPhone(phone);
        }
/*        //保存到session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));*/
        //生成token，当作登陆令牌
        String token = UUID.randomUUID().toString();
        //将User对象转为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).
                        setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token,userMap);
        //设置有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL,TimeUnit.MINUTES);
        //返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);//手机号
        user.setNickName( USER_NICK_NAME_PREFIX +RandomUtil.randomString(10));//昵称,system常量池里的常量
        //保存用户
        save(user);
        log.debug("创建用户成功{}",user);
        return user;
    }

}
