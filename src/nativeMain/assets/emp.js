// Every page works without this script: it saves reloads, reads and writes the clipboard, and filters lists
(function () {
    'use strict'

    // Forms marked data-emp-live are posted in the background. The server answers as without JavaScript, with the
    // page; the elements of the ids the attribute lists are replaced by those of the answer, the whole main when one
    // is missing on one side only
    function live(form, submitter) {
        if (form.dataset.busy) return
        var body = new URLSearchParams(new FormData(form))
        if (submitter && submitter.name) body.append(submitter.name, submitter.value)
        var buttons = form.querySelectorAll('button')
        // Taken before the buttons are disabled, which moves the focus away
        var active = document.activeElement
        var focus = active && active.dataset && (active.id || active.dataset.focus)
        form.dataset.busy = 'true'
        buttons.forEach(function (button) { button.disabled = true })
        fetch(form.action, { method: 'POST', body: body, headers: { Accept: 'text/html' } })
            .then(function (res) {
                var type = res.headers.get('Content-Type') || ''
                return type.indexOf('text/html') === 0 ? res.text() : Promise.reject(res.status)
            })
            .then(function (html) { replace(new DOMParser().parseFromString(html, 'text/html'), form.dataset.empLive.split(' '), focus) })
            .catch(function () {
                // Posted for real: the server answers it the same way
                delete form.dataset.busy
                form.removeAttribute('data-emp-live')
                buttons.forEach(function (button) { button.disabled = false })
                if (submitter) submitter.click(); else form.submit()
            })
    }

    function replace(page, ids, focus) {
        var open = [].map.call(document.querySelectorAll('details[open][id]'), function (details) { return details.id })
        var pairs = ids.map(function (id) { return [document.getElementById(id), page.getElementById(id)] })
            .filter(function (pair) { return pair[0] || pair[1] })
        if (pairs.some(function (pair) { return !pair[0] || !pair[1] })) pairs = [[document.querySelector('main'), page.querySelector('main')]]
        pairs.forEach(function (pair) { pair[0].replaceWith(document.adoptNode(pair[1])) })
        open.forEach(function (id) {
            var details = document.getElementById(id)
            if (details) details.open = true
        })
        if (focus) {
            var target = document.getElementById(focus) || document.querySelector('[data-focus="' + focus + '"]')
            if (target) target.focus({ preventScroll: true })
        }
    }

    document.addEventListener('submit', function (event) {
        var form = event.target
        if (event.defaultPrevented || !form.hasAttribute('data-emp-live') || !window.fetch) return
        event.preventDefault()
        live(form, event.submitter)
    })

    // A stepper's typed value is saved once validated
    document.addEventListener('change', function (event) {
        var field = event.target
        if (field.classList.contains('emp-stepper__value') && field.form) field.form.requestSubmit()
    })

    // A choice is saved as soon as it is made, once confirmed when the form asks for it: the box is put back otherwise
    document.addEventListener('change', function (event) {
        var field = event.target
        var form = field.form
        if (!form || !form.hasAttribute('data-submit-on-change')) return
        if (form.dataset.confirm && !window.confirm(form.dataset.confirm)) {
            if (field.type === 'checkbox') field.checked = !field.checked
            return
        }
        form.requestSubmit()
    })

    // Messages of the game: read from the clipboard, else pasted in the field, which is shown then
    document.addEventListener('click', function (event) {
        var button = event.target.closest('button[data-paste]')
        if (!button) return
        var form = button.form
        var field = form.querySelector('textarea')
        var read = navigator.clipboard && navigator.clipboard.readText ? navigator.clipboard.readText() : Promise.reject()
        read.then(function (text) {
            if (!text.trim()) return Promise.reject()
            field.value = text
            form.requestSubmit()
        }).catch(function () {
            form.classList.add('paste--manual')
            var label = form.querySelector('label[data-manual]')
            if (label) label.textContent = label.dataset.manual
            if (field.dataset.manual) field.placeholder = field.dataset.manual
            field.focus()
        })
    })

    document.addEventListener('paste', function (event) {
        var field = event.target.closest && event.target.closest('textarea[data-submit-on-paste]')
        if (field) setTimeout(function () { if (field.value.trim()) field.form.requestSubmit() })
    })

    // Copies the value of the element of the given id. Without a target, the button submits its form, posted for the
    // text to copy: the server answers it as plain text. Without the clipboard, a target is selected to be copied by
    // hand, and a form is posted for real
    function copied(button) {
        var label = button.textContent
        button.textContent = '✓ Copié'
        setTimeout(function () { button.textContent = label }, 2000)
    }

    document.addEventListener('click', function (event) {
        var button = event.target.closest('button[data-copy]')
        if (!button || button.type === 'submit') return
        var source = document.getElementById(button.dataset.copy)
        var text = 'value' in source ? source.value : source.textContent
        var written = navigator.clipboard ? navigator.clipboard.writeText(text) : Promise.reject()
        written.then(function () { copied(button) }, function () { source.focus(); if (source.select) source.select() })
    })

    document.addEventListener('submit', function (event) {
        var button = event.submitter
        if (!button || !button.hasAttribute('data-copy') || !navigator.clipboard) return
        event.preventDefault()
        var form = event.target
        // Each post may renew a link: a second tap would copy one already replaced
        if (form.dataset.busy) return
        form.dataset.busy = 'true'
        var text = fetch(form.action, { method: 'POST', headers: { Accept: 'text/plain' } })
            .then(function (res) { return res.ok ? res.text() : Promise.reject(res.status) })
        text.then(function () {}, function () {}).then(function () { delete form.dataset.busy })
        // Safari only writes during the click: the text is handed over as a promise
        var written = window.ClipboardItem
            ? navigator.clipboard.write([new ClipboardItem({ 'text/plain': text.then(function (t) { return new Blob([t], { type: 'text/plain' }) }) })])
            : text.then(function (t) { return navigator.clipboard.writeText(t) })
        written.then(function () { copied(button) }, function () { form.submit() })
    })

    // Wages: filter by pseudo, shown only when it can work
    var filter = document.querySelector('.wages-filter input')
    if (filter) {
        filter.parentElement.hidden = false
        var matches = function (element, query) { return element.dataset.pseudo.indexOf(query) >= 0 }
        filter.addEventListener('input', function () {
            var query = filter.value.trim().toLowerCase()
            // A giver's card shows all their deliveries when the giver matches, only the matching recipients otherwise
            document.querySelectorAll('.giver').forEach(function (card) {
                var giverMatches = matches(card, query)
                var lines = [].slice.call(card.querySelectorAll('.giver__delivery'))
                lines.forEach(function (line) { line.hidden = !giverMatches && !matches(line, query) })
                card.hidden = !giverMatches && lines.every(function (line) { return line.hidden })
            })
            document.querySelectorAll('.balances tbody tr').forEach(function (row) { row.hidden = !matches(row, query) })
            document.querySelectorAll('.wages-section').forEach(function (section) {
                var items = [].slice.call(section.querySelectorAll('.giver, .balances tbody tr'))
                var empty = items.length > 0 && items.every(function (item) { return item.hidden })
                var none = section.querySelector('.wages-no-match')
                if (none) none.hidden = !empty
                var table = section.querySelector('.balances')
                if (table) table.hidden = empty
            })
        })
        filter.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && filter.value !== '') {
                filter.value = ''
                filter.dispatchEvent(new Event('input'))
            }
        })
    }

    // Alliance: the rows are filtered as the pseudo is typed, the filter being sent without the script. The list is
    // replaced when a settlement is chosen: the field is found again each time
    document.addEventListener('input', function (event) {
        var search = event.target
        if (!search.matches('.alliance-search input[type=search]')) return
        var query = search.value.trim().toLowerCase()
        document.querySelectorAll('.alliance-members__table tbody tr').forEach(function (row) {
            row.hidden = row.dataset.pseudo.indexOf(query) < 0
        })
    })

    // Folded on a phone, open from a desktop width or when linked to
    ['method', 'calculation-detail'].forEach(function (id) {
        var details = document.getElementById(id)
        if (details && (matchMedia('(min-width: 48rem)').matches || location.hash === '#' + id)) details.open = true
    })
})()
