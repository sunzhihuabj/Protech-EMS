package com.projlife.org.dto;

import java.util.ArrayList;
import java.util.List;

/** 菜单树节点（MENU 型权限） */
public class MenuNode {

    public String permCode;
    public String permName;
    public Integer sortNo;
    public List<MenuNode> children = new ArrayList<>();

    public MenuNode() {
    }

    public MenuNode(String permCode, String permName, Integer sortNo) {
        this.permCode = permCode;
        this.permName = permName;
        this.sortNo = sortNo;
    }
}
