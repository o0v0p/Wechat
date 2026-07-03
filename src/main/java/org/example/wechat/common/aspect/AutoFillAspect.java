package org.example.wechat.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.wechat.common.annotation.AutoFill;
import org.example.wechat.common.constants.AutoFillConstant;
import org.example.wechat.common.constants.OperationType;
import org.example.wechat.common.util.UserContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Aspect
@Component
@Slf4j
public class AutoFillAspect {

    @Pointcut("execution(* org.example.wechat.dao.*.*(..)) && @annotation(org.example.wechat.common.annotation.AutoFill)")
    public void autoFillPointCut() {
    }

    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0 || args[0] == null) {
            return;
        }

        Object entity = args[0];
        LocalDateTime now = LocalDateTime.now();
        Long currentId = UserContext.getUserId();
        try {
            if (autoFill.value() == OperationType.INSERT) {
                invoke(entity, AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class, now);
                invoke(entity, AutoFillConstant.SET_CREATE_USER, Long.class, currentId);
                invoke(entity, AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class, now);
                invoke(entity, AutoFillConstant.SET_UPDATE_USER, Long.class, currentId);
            } else if (autoFill.value() == OperationType.UPDATE) {
                invoke(entity, AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class, now);
                invoke(entity, AutoFillConstant.SET_UPDATE_USER, Long.class, currentId);
            }
        } catch (ReflectiveOperationException e) {
            log.warn("自动填充公共字段失败: entity={}, operation={}",
                    entity.getClass().getSimpleName(), autoFill.value(), e);
        }
    }

    private void invoke(Object target, String methodName, Class<?> parameterType, Object value)
            throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterType);
        method.invoke(target, value);
    }
}
