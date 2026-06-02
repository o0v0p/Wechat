package org.example.wechat.pojo.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FriendListVO {

    private Long friendID;
    private String friendName;
    private String nickname;
    private String avatar;
    private String chatID;
    private Long categoryID;
}
