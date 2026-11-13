package ru.vadim.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Разговор с барменом Тимофеем. Без сервера: просто реплики. Он не наливает, пока не сыграете,
 * и про Вадима говорит шёпотом.
 */
public class BarmanScreen extends Screen {
	private record Line(String ask, String answer) {}

	private static final List<Line> LINES = List.of(
			new Line("Налей чего-нибудь", "Сначала игра, потом бар. Такие правила заведения. Кнопка «Готов» — у двери, когда все соберутся."),
			new Line("Кто такой Вадим?", "Тсс. Не здесь. Он всё слышит… Говорят, сидит где-то на своём троне и смотрит."),
			new Line("Что там по телеку?", "Опять люди пропадают. Третий случай за месяц. Одному, вроде, удалось выбраться… Включу потом, сами посмотрите."),
			new Line("Что за лопаты на стене?", "Музейные экспонаты. Лопата дежаву и лопата аниме-психа. Тронешь — будешь мыть полы до утра."),
			new Line("А записки?", "Записки Короля. Все шестнадцать. Собирали всей толпой, теперь висят. Почитай, мудрые вещи пишет.")
	);

	private String answer = "Чего надо? Только быстро, у меня тут смена.";

	public BarmanScreen() {
		super(Component.literal("Тимофей"));
	}

	@Override
	protected void init() {
		int w = 230, x = width / 2 - w / 2;
		int y = height / 2 + 6;
		for (Line l : LINES) {
			addRenderableWidget(Button.builder(Component.literal("— " + l.ask()), b -> answer = l.answer()).bounds(x, y, w, 18).build());
			y += 20;
		}
		addRenderableWidget(Button.builder(Component.literal("Ладно, я пошёл"), b -> onClose()).bounds(x, y + 4, w, 18).build());
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		super.render(g, mx, my, pt);
		int w = 300, x = width / 2 - w / 2, y = height / 2 - 92;
		g.fill(x - 8, y - 8, x + w + 8, y + 84, 0xC0100A06);
		g.fill(x - 8, y - 8, x + w + 8, y - 7, 0xFFC9A24A);
		g.drawString(font, Component.literal("ТИМОФЕЙ, бармен").withStyle(s -> s.withBold(true)), x, y, 0xFFE8C77A);
		int ty = y + 16;
		for (FormattedCharSequence line : font.split(Component.literal("«" + answer + "»"), w)) {
			g.drawString(font, line, x, ty, 0xFFEDE6D6);
			ty += 11;
		}
	}

	@Override public boolean isPauseScreen() { return false; }
}
