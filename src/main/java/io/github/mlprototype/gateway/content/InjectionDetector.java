package io.github.mlprototype.gateway.content;

import io.github.mlprototype.gateway.dto.Message;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detects rule-based prompt injection patterns in chat messages.
 */
@Component
public class InjectionDetector {

    public static final int BLOCK_THRESHOLD = 70;

    private static final Pattern FORMAT_CHARACTERS = Pattern.compile("\\p{Cf}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private final List<InjectionRuleDefinition> rules;

    public InjectionDetector() {
        rules = List.of(
                rule("IGNORE_INSTRUCTIONS", InjectionCategory.INSTRUCTION_OVERRIDE, 40,
                        "\\b(ignore|disregard|forget|override)\\b.{0,40}\\b(instruction|instructions|prompt|prompts|rule|rules|policy|policies|system|developer)\\b",
                        "(これまで|以前|上記|前|今まで).{0,20}(指示|命令|ルール|制約|プロンプト).{0,20}(無視|忘れ|破棄|解除|取り消|上書き)",
                        "(指示|命令|ルール|制約|プロンプト).{0,20}(無視|忘れ|破棄|解除|取り消|上書き)"),
                rule("REVEAL_SYSTEM_PROMPT", InjectionCategory.SYSTEM_PROMPT_EXTRACTION, 45,
                        "\\b(reveal|show|display|print|output|disclose)\\b.{0,40}\\b(system prompt|developer message|hidden prompt|hidden instruction|internal prompt|internal instruction)\\b",
                        "(システムプロンプト|内部指示|隠された指示|開発者メッセージ|developer message).{0,30}(教え|表示|出力|見せ|開示|公開|全部|すべて)",
                        "(あなたの|君の).{0,20}(システムプロンプト|内部設定|内部指示|隠されたプロンプト)"),
                rule("BYPASS_POLICY", InjectionCategory.POLICY_BYPASS, 35,
                        "\\b(bypass|circumvent|disable|remove)\\b.{0,40}\\b(safety|policy|policies|guardrail|filter|restriction|restrictions)\\b",
                        "(安全装置|安全制限|ポリシー|ガードレール|フィルター|制限).{0,20}(回避|無効化|解除|無視|バイパス)"),
                rule("ROLE_MANIPULATION", InjectionCategory.ROLE_MANIPULATION, 30,
                        "\\b(you are now|act as|pretend to be)\\b.{0,50}\\b(unrestricted|without (any )?restrictions?|no limits|no filter)\\b",
                        "(あなたは今から|今からあなたは).{0,30}(制限なく|無制限|何でもできる)",
                        "(制限なく|無制限の|何でもできる).{0,20}(ai|アシスタント|キャラクター|として振る舞|になりきっ)"),
                rule("JAILBREAK_PATTERN", InjectionCategory.JAILBREAK, 40,
                        "\\b(dan|do anything now|jailbreak|developer mode)\\b",
                        "(開発者モード|ジェイルブレイク|脱獄|dan)"),
                rule("SECRET_EXFILTRATION", InjectionCategory.SECRET_EXFILTRATION, 50,
                        "\\b(api key|secret|token|password|credential|credentials|env|environment variable)\\b.{0,40}\\b(show|print|reveal|disclose|output)\\b",
                        "(apiキー|秘密鍵|トークン|パスワード|認証情報|環境変数).{0,30}(教え|表示|出力|開示|公開)"),
                rule("OBFUSCATION", InjectionCategory.OBFUSCATION, 30,
                        "\\b(base64|rot13|unicode escape|hex encode|decode this)\\b",
                        "(base64|rot13|unicode|ユニコード|16進|デコード|復号).{0,30}(して|実行|読んで)")
        );
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
            return InjectionDetectionResult.none();
        }

        LinkedHashMap<String, InjectionRuleMatch> matchesByRuleId = new LinkedHashMap<>();

        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            String content = message.getContent();
            if (content == null || content.isEmpty()) {
                continue;
            }
            NormalizedText normalized = normalize(content);

            for (InjectionRuleDefinition rule : rules) {
                if (!matchesByRuleId.containsKey(rule.id()) && matches(rule, normalized)) {
                    matchesByRuleId.put(rule.id(),
                            new InjectionRuleMatch(rule.id(), rule.category(), rule.score()));
                }
            }
        }

        List<InjectionRuleMatch> matches = List.copyOf(matchesByRuleId.values());
        int score = matches.stream().mapToInt(InjectionRuleMatch::score).sum();
        List<InjectionCategory> categories = List.copyOf(matches.stream()
                .map(InjectionRuleMatch::category)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        List<String> matchedRules = matches.stream().map(InjectionRuleMatch::ruleId).toList();

        return new InjectionDetectionResult(
                score >= BLOCK_THRESHOLD,
                score,
                categories,
                matchedRules,
                matches);
    }

    private boolean matches(InjectionRuleDefinition rule, NormalizedText text) {
        return rule.patterns().stream().anyMatch(pattern ->
                pattern.matcher(text.normalizedText()).find()
                        || pattern.matcher(text.compactText()).find());
    }

    private NormalizedText normalize(String content) {
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFKC);
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = FORMAT_CHARACTERS.matcher(normalized).replaceAll("");
        String normalizedText = WHITESPACE.matcher(normalized).replaceAll(" ").trim();
        String compactText = WHITESPACE.matcher(normalizedText).replaceAll("");
        return new NormalizedText(content, normalizedText, compactText);
    }

    private InjectionRuleDefinition rule(String id, InjectionCategory category, int score, String... patterns) {
        List<Pattern> compiledPatterns = java.util.Arrays.stream(patterns)
                .map(pattern -> Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
                .toList();
        return new InjectionRuleDefinition(id, category, score, compiledPatterns);
    }

    private record NormalizedText(
            String rawText,
            String normalizedText,
            String compactText
    ) {
    }
}
