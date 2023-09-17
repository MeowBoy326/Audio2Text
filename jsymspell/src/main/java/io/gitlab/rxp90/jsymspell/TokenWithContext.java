package io.gitlab.rxp90.jsymspell;

import java.util.List;

import io.gitlab.rxp90.jsymspell.api.SuggestItem;

public class TokenWithContext {
    public String originalToken;
    public List<SuggestItem> suggestions;
    public boolean isCombination;

    public TokenWithContext(String originalToken, List<SuggestItem> suggestions, boolean isCombination) {
        this.originalToken = originalToken;
        this.suggestions = suggestions;
        this.isCombination = isCombination;
    }
}