package controller;

import com.anime.common.dto.UserLoginDTO;
import com.anime.common.result.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/hello")
    public Result<String> test() {
        return Result.success("Hello from anime community backend!");
    }

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("pong");
    }

    // 新增：测试接收前端数据的接口
    @PostMapping("/login")
    public Result<String> testLogin(@RequestBody UserLoginDTO loginDTO) {
        // 打印接收到的数据
        System.out.println("=== 接收到前端数据 ===");
        System.out.println("用户名/邮箱: " + loginDTO.getUsernameOrEmail());
        System.out.println("密码: " + loginDTO.getPassword());
        System.out.println("=====================");

        // 简单的处理逻辑
        String responseMessage = String.format(
                "✅ 数据接收成功！\n用户: %s\n密码长度: %d位",
                loginDTO.getUsernameOrEmail(),
                loginDTO.getPassword(). length()
        );

        return Result.success(responseMessage);
    }

    // 新增：测试GET请求传参
    @GetMapping("/user/{userId}")
    public Result<String> testPathVariable(@PathVariable Long userId) {
        return Result.success("接收到用户ID: " + userId);
    }

    // 新增：测试查询参数
    @GetMapping("/search")
    public Result<String> testQueryParams(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page) {

        String result = String.format("搜索关键字: %s, 页码: %d", keyword, page);
        return Result.success(result);
    }
}