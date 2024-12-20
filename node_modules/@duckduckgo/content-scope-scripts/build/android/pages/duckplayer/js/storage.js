function deleteStorage(subject) {
    Object.keys(subject).forEach((key) => {
        if (key.indexOf('yt-player') === 0) {
            return;
        }
        subject.removeItem(key);
    });
}

function deleteAllCookies() {
    const cookies = document.cookie.split(';');
    for (let i = 0; i < cookies.length; i++) {
        const cookie = cookies[i];
        const eqPos = cookie.indexOf('=');
        const name = eqPos > -1 ? cookie.substr(0, eqPos) : cookie;
        document.cookie = name + '=;expires=Thu, 01 Jan 1970 00:00:00 GMT;domain=youtube-nocookie.com;path=/;';
    }
}

export function initStorage() {
    window.addEventListener('unload', () => {
        deleteStorage(localStorage);
        deleteStorage(sessionStorage);
        deleteAllCookies();
    });

    window.addEventListener('load', () => {
        deleteStorage(localStorage);
        deleteStorage(sessionStorage);
        deleteAllCookies();
    });
}
