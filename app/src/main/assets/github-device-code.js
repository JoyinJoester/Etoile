(function (code) {
    // The URL is checked again here because navigation may race evaluateJavascript.
    if (location.protocol !== 'https:' || location.hostname !== 'github.com' ||
        (location.port && location.port !== '443') ||
        !/^\/login\/device\/?$/.test(location.pathname) ||
        !/^[A-Z0-9]{8}$/.test(code)) return false;

    const editable = input => !input.disabled && !input.readOnly &&
        ['text', 'tel', ''].includes(input.getAttribute('type') || '') &&
        input.getClientRects().length > 0;
    const setValue = (input, value) => {
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('change', { bubbles: true }));
    };
    // GitHub's device page uses eight single-character inputs. Do not paste
    // the complete code into one cell or include the display-only separator.
    const groups = [...document.querySelectorAll('form')];
    if (groups.length === 0) groups.push(document);
    for (const group of groups) {
        const cells = [...group.querySelectorAll('input[maxlength="1"]')].filter(editable);
        if (cells.length !== code.length) continue;
        cells.forEach((input, index) => setValue(input, code[index]));
        return cells.every((input, index) => input.value === code[index]);
    }
    // Also support the older single-input device form.
    const input = [...document.querySelectorAll(
        'input[name="otp"], input[name="user_code"], input[autocomplete="one-time-code"]'
    )].find(input => editable(input) && (input.maxLength < 0 || input.maxLength >= 8));
    if (!input) return false;
    const value = input.maxLength === 8 ? code : code.slice(0, 4) + '-' + code.slice(4);
    setValue(input, value);
    return input.value === value;
})(__ETOILE_DEVICE_CODE__);
