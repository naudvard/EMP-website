package fr.nathanaudvard.emp.web.pages

import kotlinx.html.A
import kotlinx.html.BUTTON
import kotlinx.html.ButtonType
import kotlinx.html.DETAILS
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.FlowOrPhrasingContent
import kotlinx.html.InputType
import kotlinx.html.SECTION
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.summary
import kotlinx.html.unsafe

// The design system components (emp-* classes): colours, heights and radii come from their classes only

private fun classes(base: String, vararg modifiers: String?): String =
    listOf(base, *modifiers).filterNotNull().joinToString(" ")

enum class CardVariant(internal val cssClass: String?) {
    DEFAULT(null),
    LARGE("emp-card--large"),
    ACCENT("emp-card--accent"),
    WARNING("emp-card--warning"),
}

fun FlowContent.empCard(variant: CardVariant = CardVariant.DEFAULT, cssClass: String? = null, block: SECTION.() -> Unit) =
    section(classes("emp-card", variant.cssClass, cssClass), block)

enum class BtnVariant(internal val cssClass: String) {
    PRIMARY("emp-btn--primary"),
    SECONDARY("emp-btn--secondary"),
    SUBTLE("emp-btn--subtle"),
    LINK("emp-btn--link"),
}

enum class BtnSize(internal val cssClass: String?) {
    DEFAULT(null),
    COMPACT("emp-btn--compact"),
    SMALL("emp-btn--small"),
}

fun FlowOrPhrasingContent.empBtn(
    variant: BtnVariant,
    size: BtnSize = BtnSize.DEFAULT,
    type: ButtonType = ButtonType.submit,
    fullWidth: Boolean = false,
    block: BUTTON.() -> Unit,
) = button(type = type, classes = classes("emp-btn", variant.cssClass, size.cssClass, "emp-btn--block".takeIf { fullWidth }), block = block)

// A link that looks like a button, for an action that only navigates
fun FlowOrPhrasingContent.empBtnLink(
    href: String,
    variant: BtnVariant,
    size: BtnSize = BtnSize.DEFAULT,
    block: A.() -> Unit,
) = a(href, classes = classes("emp-btn", variant.cssClass, size.cssClass), block = block)

enum class BadgeVariant(internal val cssClass: String) {
    YOU("emp-badge--you"),
    LEADER("emp-badge--leader"),
    NEUTRAL("emp-badge--neutral"),
    SOLID("emp-badge--solid"),
    RANK("emp-badge--rank"),
}

fun FlowOrPhrasingContent.empBadge(variant: BadgeVariant, text: String) =
    span(classes("emp-badge", variant.cssClass)) { +text }

enum class MessageVariant(internal val cssClass: String, internal val role: String) {
    SUCCESS("emp-message--success", "status"),
    WARNING("emp-message--warning", "status"),
    DANGER("emp-message--danger", "alert"),
    INFO("emp-message--info", "status"),
}

fun FlowContent.empMessage(variant: MessageVariant, inline: Boolean = false, block: DIV.() -> Unit) =
    div(classes("emp-message", variant.cssClass, "emp-message--inline".takeIf { inline })) {
        attributes["role"] = variant.role
        block()
    }

enum class ProgressThickness(internal val cssClass: String?) {
    DEFAULT(null),
    THIN("emp-progress--thin"),
    HAIRLINE("emp-progress--hairline"),
}

fun FlowContent.empProgress(
    done: Long,
    total: Long,
    label: String,
    thickness: ProgressThickness = ProgressThickness.DEFAULT,
) = div(classes("emp-progress", thickness.cssClass)) {
    val percent = if (total <= 0) 100 else (done.coerceIn(0, total) * 100 / total).toInt()
    attributes["role"] = "progressbar"
    attributes["aria-label"] = label
    attributes["aria-valuemin"] = "0"
    attributes["aria-valuemax"] = "100"
    attributes["aria-valuenow"] = percent.toString()
    div("emp-progress__bar") { attributes["style"] = "width: $percent%" }
}

// A line of a list: its text, then what is done with it
fun FlowContent.empRow(title: String, text: DIV.() -> Unit = {}, actions: DIV.() -> Unit = {}) =
    div("emp-row") {
        div("emp-row__text") {
            span("emp-row__title") { +title }
            text()
        }
        actions()
    }

fun FlowOrPhrasingContent.empRowDone(text: String) = span("emp-row__done") { +text }

// The buttons step the field, which holds the value sent with the form
fun FlowContent.empStepper(name: String, value: Long, label: String, max: Long, compact: Boolean = false) =
    div(classes("emp-stepper", "emp-stepper--compact".takeIf { compact })) {
        button(type = ButtonType.button, classes = "emp-stepper__minus") {
            attributes["data-step"] = "-1"
            attributes["aria-label"] = "Baisser $label"
            unsafe { +MINUS_ICON }
        }
        input(type = InputType.number, name = name, classes = "emp-stepper__value") {
            this.value = value.toString()
            min = "0"
            this.max = max.toString()
            attributes["inputmode"] = "numeric"
            attributes["aria-label"] = label
        }
        button(type = ButtonType.button, classes = "emp-stepper__plus") {
            attributes["data-step"] = "1"
            attributes["aria-label"] = "Monter $label"
            unsafe { +PLUS_ICON }
        }
    }

// Opens without JavaScript
fun FlowContent.empDisclosure(label: String, card: Boolean = false, id: String? = null, cssClass: String? = null, block: DETAILS.() -> Unit) =
    details(cssClass) {
        id?.let { this.id = it }
        summary(classes("emp-disclosure", "emp-disclosure--card".takeIf { card })) { +label }
        block()
    }

// Copies the value of the element of the given id. Without a target, the button submits its form, posted for the text
// to copy: the server answers it as plain text to the script, as a page showing it to copy by hand otherwise.
// The pages using it add copyScript()
fun FlowOrPhrasingContent.empCopyButton(
    label: String,
    variant: BtnVariant,
    size: BtnSize = BtnSize.DEFAULT,
    target: String? = null,
) = empBtn(variant, size, if (target == null) ButtonType.submit else ButtonType.button) {
    attributes["data-copy"] = target.orEmpty()
    +label
}

fun FlowContent.copyScript() = script { unsafe { +COPY_SCRIPT } }

// Without the clipboard, a target is selected to be copied by hand, and a form is posted for real
private const val COPY_SCRIPT = """
(function () {
    function copied(button) {
        var label = button.textContent;
        button.textContent = "✓ Copié";
        setTimeout(function () { button.textContent = label; }, 2000);
    }
    document.addEventListener("click", function (event) {
        var button = event.target.closest("button[data-copy]");
        if (!button || button.type === "submit") return;
        var source = document.getElementById(button.dataset.copy);
        var text = "value" in source ? source.value : source.textContent;
        var written = navigator.clipboard ? navigator.clipboard.writeText(text) : Promise.reject();
        written.then(function () { copied(button); }, function () { source.focus(); if (source.select) source.select(); });
    });
    document.addEventListener("submit", function (event) {
        var button = event.submitter;
        if (!button || !button.hasAttribute("data-copy") || !navigator.clipboard) return;
        event.preventDefault();
        var form = event.target;
        var text = fetch(form.action, { method: "POST", headers: { Accept: "text/plain" } })
            .then(function (res) { return res.ok ? res.text() : Promise.reject(res.status); });
        // Safari only writes during the click: the text is handed over as a promise
        var written = window.ClipboardItem
            ? navigator.clipboard.write([new ClipboardItem({ "text/plain": text.then(function (t) { return new Blob([t], { type: "text/plain" }); }) })])
            : text.then(function (t) { return navigator.clipboard.writeText(t); });
        written.then(function () { copied(button); }, function () { form.submit(); });
    });
})();
"""

private const val MINUS_ICON =
    """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M5 12h14"/></svg>"""

private const val PLUS_ICON =
    """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>"""
