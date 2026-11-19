let currentDir = "/storage/emulated/0";

const bootCheck = setInterval(() => {
    if (window.Win11Native) {
        const status = JSON.parse(Win11Native.getEngineStatus());
        document.getElementById("boot-status").innerText = status.message;
        document.getElementById("engine-badge").innerText = "⚡ " + status.message;

        if (status.ready) {
            clearInterval(bootCheck);
            const bootScreen = document.getElementById("boot-screen");
            bootScreen.style.opacity = "0";
            setTimeout(() => bootScreen.style.display = "none", 500);
        }
    } else {
        clearInterval(bootCheck);
        document.getElementById("boot-screen").style.display = "none";
    }
}, 800);

function toggleStartMenu(e) {
    e.stopPropagation();
    document.getElementById("start-menu").classList.toggle("hidden");
}

function closeStartMenu() {
    document.getElementById("start-menu").classList.add("hidden");
}

function closeWindow(id) {
    document.getElementById(id).classList.add("hidden");
}

function openExplorer(path) {
    currentDir = path;
    document.getElementById("explorer-window").classList.remove("hidden");
    document.getElementById("current-path").innerText = path;
    loadFiles(path);
}

function goBackDir() {
    if (currentDir === "/storage/emulated/0" || currentDir === "/") return;
    let parent = currentDir.substring(0, currentDir.lastIndexOf("/"));
    if (!parent) parent = "/storage/emulated/0";
    openExplorer(parent);
}

function loadFiles(path) {
    const container = document.getElementById("file-list");
    container.innerHTML = "";

    let files = [];
    if (window.Win11Native) {
        const raw = Win11Native.listFiles(path);
        files = JSON.parse(raw);
    } else {
        files = [
            { name: "Download", path: path + "/Download", isDir: true },
            { name: "app_setup.exe", path: path + "/app_setup.exe", isDir: false }
        ];
    }

    files.sort((a, b) => b.isDir - a.isDir || a.name.localeCompare(b.name));

    files.forEach(file => {
        const isExe = !file.isDir && file.name.toLowerCase().endsWith(".exe");
        const div = document.createElement("div");
        div.className = "file-item" + (isExe ? " exe-badge" : "");

        let icon = "📄";
        if (file.isDir) icon = "📁";
        else if (isExe) icon = "🪟";

        div.innerHTML = `<div class="f-icon">${icon}</div><div class="f-name">${file.name}</div>`;

        div.onclick = () => {
            if (file.isDir) {
                openExplorer(file.path);
            } else if (isExe) {
                if (window.Win11Native) {
                    Win11Native.runExe(file.path);
                } else {
                    alert("اجرای فایل ویندوزی: " + file.name);
                }
            }
        };

        container.appendChild(div);
    });
}

const win = document.getElementById("explorer-window");
const header = document.getElementById("explorer-header");
let isDragging = false, offsetX, offsetY;

header.addEventListener("pointerdown", e => {
    isDragging = true;
    offsetX = e.clientX - win.offsetLeft;
    offsetY = e.clientY - win.offsetTop;
});
document.addEventListener("pointermove", e => {
    if (!isDragging) return;
    win.style.left = (e.clientX - offsetX) + "px";
    win.style.top = (e.clientY - offsetY) + "px";
});
document.addEventListener("pointerup", () => isDragging = false);

setInterval(() => {
    const now = new Date();
    document.getElementById("clock").innerText = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}, 1000);

