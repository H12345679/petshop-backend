package com.petshop.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

/**
 * Mock AI 实现：返回宠物养护相关的预设回答。
 * 当 ai.provider=mock（或不配置）时生效。
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiProvider {

    private final Random random = new Random();

    private static final List<String> ANSWERS = List.of(
        "猫咪拉肚子可能由于饮食不当、寄生虫或受凉引起。如果是轻微消化不良，可以禁食半天并喂食益生菌调理；如果是持续腹泻，建议前往宠物医院检查，切勿盲目乱用人药。",
        "猫咪一般在两个月大时，身体健康的情况下开始打第一针传染病疫苗（通常是猫三联），之后每隔3-4周加强一针，共需3针。狂犬疫苗建议在3个月大以后接种。",
        "狗狗每天建议喂食2-3次，幼犬可以增加到3-4次。喂食量根据狗粮包装上的建议克数按体重计算，注意不要过量，保持充足饮水。",
        "宠物掉毛常见原因：季节性换毛、营养不均衡、皮肤病或寄生虫。建议定期梳毛、补充鱼油和卵磷脂，如果伴随皮肤红肿或瘙痒请尽快就医。",
        "小猫到家第一周建议先关在小房间适应环境，准备好猫砂盆、食物和水。不要强行抱它，让它自己慢慢探索，等它主动靠近你后再互动。",
        "狗狗频繁挠耳朵可能是耳螨或细菌感染，观察耳道是否有黑色分泌物或异味。建议用宠物专用洗耳液清洁，严重时需就医。",
        "兔子的主食应该是干草（提摩西草），占饮食80%以上，搭配少量兔粮和新鲜蔬菜。胡萝卜含糖高不能多喂，保持饮水清洁。",
        "鹦鹉换羽期会掉大量羽毛，持续2-3个月。期间注意补充蛋白质和钙质，保持环境安静温暖，避免惊吓。"
    );

    @Override
    public void streamChat(String question, String context, java.util.function.Consumer<String> onMessage, Runnable onComplete, java.util.function.Consumer<Throwable> onError) {
        String answer = ANSWERS.get(random.nextInt(ANSWERS.size()));
        
        // 简单加点商品推荐，展示 Markdown
        if (context != null && !context.trim().isEmpty()) {
            answer += "\n\n顺便向您推荐：[【测试营养罐头】](/product/1010)";
        }

        String finalAnswer = answer;
        new Thread(() -> {
            try {
                // 模拟网络延迟
                Thread.sleep(500);
                // 模拟打字机效果，逐字发送
                for (char c : finalAnswer.toCharArray()) {
                    onMessage.accept(String.valueOf(c));
                    Thread.sleep(30); // 每个字停顿 30ms
                }
                onComplete.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                onError.accept(e);
            } catch (Exception e) {
                onError.accept(e);
            }
        }).start();
    }

    @Override
    public String chat(String question, String context) {
        return ANSWERS.get(random.nextInt(ANSWERS.size()));
    }
}
