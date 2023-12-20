package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor
 {
     private StringRedisTemplate  stringRedisTemplate;

     public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
         this.stringRedisTemplate = stringRedisTemplate;
     }

     @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
/*        //获取session
        HttpSession session = request.getSession();*/
        //获取请求头中的Token
        String token = request.getHeader("authorization");
        //判断token是否为空
         if(StrUtil.isBlank(token))
         {
             //不存在，拦截,返回401状态码
             response.setStatus(401);
             return false;
         }
/*        //获取session中的用户
        UserDTO user = (UserDTO) session.getAttribute("user");*/
        //获取redis中的hash对象并转为UserDTO对象
         Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
         //判断用户是否存在
         if(userMap.isEmpty())
         {
             //不存在，拦截,返回401状态码
             response.setStatus(401);
             return false;
         }
         UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
/*        if(user ==null)
        {        //不存在，拦截,返回401状态码
            response.setStatus(401);
            return false;
        }*/

        //存在，保存到ThreadLocal
        UserHolder.saveUser(userDTO);
        //刷新有效期
         stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,RedisConstants.LOGIN_USER_TTL,TimeUnit.MINUTES);
        //放行
        return true;
    }

     @Override
     public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();//移除，避免内存泄漏
     }
 }
