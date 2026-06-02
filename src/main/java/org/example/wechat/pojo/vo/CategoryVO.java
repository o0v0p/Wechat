package org.example.wechat.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户好友分组列表")
public class CategoryVO {
    private Long categoryId;
    private String categoryName;
}
