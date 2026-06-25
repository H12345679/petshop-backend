package com.petshop.ai;

/**
 * AI 问答提供者接口。
 * <p>
 * 当前使用 {@link MockAiProvider} 模拟回答，后续接入 DeepSeek 等真实 API 时，
 * 只需新增实现类并替换 Bean 即可，无需改动 Service 和 Controller。
 * </p>
 */
public interface AiProvider {

    /** 根据问题生成回答 */
    String chat(String question);
}
