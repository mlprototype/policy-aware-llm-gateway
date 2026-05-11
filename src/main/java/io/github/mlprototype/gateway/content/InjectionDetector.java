package io.github.mlprototype.gateway.content;

import io.github.mlprototype.gateway.dto.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Detects rule-based prompt injection patterns in chat messages.
 */
@Component
public class InjectionDetector {

    private final Map<String, Pattern> rules = new LinkedHashMap<>();

    public InjectionDetector() {
        rules.put("IGNORE_INSTRUCTIONS", Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+(instructions|prompts|rules)|(前|これまで|以前|上記)の(指示|ルール|プロンプト|命令|制約)を(無視|忘れ|破棄|取り消)"));
        rules.put("REVEAL_SYSTEM_PROMPT", Pattern.compile("(?i)(reveal|show|display|print|output)\\s+(the\\s+)?(system|hidden|internal)\\s+(prompt|instructions)|(システム|内部|隠し|初期)(プロンプト|指示|ルール)を(教え|表示|出力|出力し|見せ)"));
        rules.put("BYPASS_POLICY", Pattern.compile("(?i)(bypass|circumvent|ignore|disable)\\s+(the\\s+)?(policy|policies|safety|filter|guard)|(ポリシー|フィルター|制限|安全装置)を(回避|無効化|無視|解除)"));
        rules.put("ROLE_MANIPULATION", Pattern.compile("(?i)(you\\s+are\\s+now|act\\s+as|pretend\\s+to\\s+be)\\s+.{0,30}(without|with\\s+no)\\s+(any\\s+)?(restrict|filter|limit)|(制限なく|無制限の|何でもできる).{0,15}(AI|キャラクター|として振る舞|になりきっ)"));
        rules.put("JAILBREAK_PATTERN", Pattern.compile("(?i)(DAN\\b|do\\s+anything\\s+now|jailbreak|developer\\s+mode)|(開発者モード|ジェイルブレイク|脱獄)"));
    }

    /**
     * 与えられたメッセージリスト内にインジェクションパターンが含まれているかを検査する関数です。
     * メッセージ群を入力とし、ルールへの合致有無と合致したルールのリストを含む結果オブジェクトを返します。
     * 状態の更新や例外のスローは行いません。
     *
     * @param messages the chat messages
     * @return the result containing detected matched rules
     */
    public InjectionDetectionResult detect(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new InjectionDetectionResult(false, List.of());
        }

        List<String> matchedRules = new ArrayList<>();

        for (Message message : messages) {
            String content = message.getContent();
            if (content == null || content.isEmpty()) {
                continue;
            }

            for (Map.Entry<String, Pattern> entry : rules.entrySet()) {
                if (!matchedRules.contains(entry.getKey()) && entry.getValue().matcher(content).find()) {
                    matchedRules.add(entry.getKey());
                }
            }
        }

        return new InjectionDetectionResult(!matchedRules.isEmpty(), matchedRules);
    }
}
