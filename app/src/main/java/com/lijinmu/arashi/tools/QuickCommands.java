package com.lijinmu.arashi.tools;

import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;

public final class QuickCommands extends BasePage {

    private final QuickCommandController commands = new QuickCommandController(this);

    @Override protected void populate() {
        MiuixPages.renderQuickCommands(this);
    }

    public interface Completion {
        void onComplete(boolean success, String output);
    }

    public void queryRecommendedFrozen(Completion completion) {
        commands.queryRecommendedFrozen(completion);
    }

    public void setRecommendedFrozen(boolean freeze, Completion completion) {
        commands.setRecommendedFrozen(freeze, completion);
    }

    public void run(String title, String command, Completion completion) {
        commands.run(title, command, completion);
    }
}
