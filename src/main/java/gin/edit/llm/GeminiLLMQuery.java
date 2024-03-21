package gin.edit.llm;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import dev.langchain4j.service.AiServices;

import java.time.Duration;

public class GeminiLLMQuery implements LLMQuery {
    interface Chat {
        String chat(String userMessage);
    }

    private Chat chat;

    public GeminiLLMQuery() {

        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .modelName(LLMConfig.geminiModelName).project(LLMConfig.geminiProjectName)
                .location(LLMConfig.geminiLocation)
                .build();

        chat = AiServices.builder(Chat.class)
                .chatLanguageModel(model)
                .build();

    }

    @Override
    public boolean testServerReachable() {
        return true;
    }

    @Override
    public String chatLLM(String prompt) {
        return chat.chat(prompt);
    }
}
