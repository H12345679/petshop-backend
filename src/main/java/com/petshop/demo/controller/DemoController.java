package com.petshop.demo.controller;

import com.petshop.common.BusinessException;
import com.petshop.common.PageResult;
import com.petshop.common.Result;
import com.petshop.demo.entity.DemoItem;
import com.petshop.demo.service.DemoItemPageQuery;
import com.petshop.demo.service.DemoItemService;
import com.petshop.security.JwtUtil;
import com.petshop.security.RequireLogin;
import com.petshop.security.RequireRole;
import com.petshop.security.UserContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 框架自检 Demo —— 验证 A2~A6 的公共框架是否跑通。
 * <p>
 * 包含两部分：
 * <ul>
 *   <li>鉴权演示：ping / login / me / admin（B 实现正式登录后可删）</li>
 *   <li>CRUD 演示：DemoItem 增删改查 + 分页（展示完整三层架构用法，供 B/C/D/E 参考）</li>
 * </ul>
 */
@Tag(name = "00-框架自检Demo")
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private DemoItemService demoItemService;

    // ==================== 鉴权演示 ====================

    @Operation(summary = "公开接口：健康检查")
    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("pong");
    }

    @Operation(summary = "演示登录：签发测试 token（正式登录由 B 实现）")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestParam(defaultValue = "tester") String username,
                                             @RequestParam(defaultValue = "USER") String role) {
        String token = jwtUtil.createToken(1L, username, role);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", username);
        data.put("role", role);
        return Result.success("登录成功", data);
    }

    @Operation(summary = "需要登录：返回当前登录用户")
    @RequireLogin
    @GetMapping("/me")
    public Result<UserContext.LoginUser> me() {
        return Result.success(UserContext.get());
    }

    @Operation(summary = "需要管理员：仅 ADMIN 角色可访问")
    @RequireRole({"ADMIN"})
    @GetMapping("/admin")
    public Result<String> adminOnly() {
        return Result.success("你是管理员：" + UserContext.get().getUsername());
    }

    // ==================== CRUD 演示（DemoItem）====================

    @Operation(summary = "新增 DemoItem")
    @PostMapping("/items")
    public Result<DemoItem> create(@RequestBody DemoItem item) {
        demoItemService.save(item);
        return Result.success(item);
    }

    @Operation(summary = "根据 ID 查询 DemoItem")
    @GetMapping("/items/{id}")
    public Result<DemoItem> getById(@PathVariable Long id) {
        DemoItem item = demoItemService.getById(id);
        if (item == null) {
            throw new BusinessException("DemoItem 不存在：" + id);
        }
        return Result.success(item);
    }

    @Operation(summary = "修改 DemoItem")
    @PutMapping("/items/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DemoItem item) {
        item.setId(id);
        boolean ok = demoItemService.updateById(item);
        if (!ok) {
            throw new BusinessException("更新失败，记录不存在：" + id);
        }
        return Result.success();
    }

    @Operation(summary = "删除 DemoItem（逻辑删除）")
    @DeleteMapping("/items/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean ok = demoItemService.removeById(id);
        if (!ok) {
            throw new BusinessException("删除失败，记录不存在：" + id);
        }
        return Result.success();
    }

    @Operation(summary = "分页查询 DemoItem（支持名称模糊搜索）")
    @GetMapping("/items")
    public Result<PageResult<DemoItem>> page(DemoItemPageQuery query) {
        return Result.success(demoItemService.page(query));
    }
}
