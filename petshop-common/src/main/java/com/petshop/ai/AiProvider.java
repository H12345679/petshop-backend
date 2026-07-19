package com.petshop.ai;

/**
 * AI 问答提供者接口。
 * <p>
 * 当前使用 {@link MockAiProvider} 模拟回答，后续接入 DeepSeek 等真实 API 时，
 * 只需新增实现类并替换 Bean 即可，无需改动 Service 和 Controller。
 * </p>
 */
public interface AiProvider {

    /** 根据问题和背景上下文生成回答 */
    String chat(String question, String context);

    /** 流式生成回答 */
    default void streamChat(String question, String context, java.util.function.Consumer<String> onMessage, Runnable onComplete, java.util.function.Consumer<Throwable> onError) {
        throw new UnsupportedOperationException("该 AI Provider 暂不支持流式输出");
    }

    /** 根据问题生成回答 */
    default String chat(String question) {
        return chat(question, null);
    }

    /** 
     * 将文本转换为向量表示 (Embedding)
     * 用于 RAG (检索增强生成) 的向量检索
     */
    default java.util.List<Double> getEmbedding(String text) {
        throw new UnsupportedOperationException("该 AI Provider 暂不支持生成 Embedding");
    }
}
