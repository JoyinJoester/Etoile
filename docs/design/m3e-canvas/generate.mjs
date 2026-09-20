// Editable Etoile layouts for https://github.com/lnkiai/m3e-canvas.
// Protocol: https://lnkiai.github.io/m3e-canvas/agent.md (2026-09-16).
// No dependencies, network calls or private account data.
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join, dirname } from 'node:path';
import { deflateRawSync } from 'node:zlib';

const directory = dirname(fileURLToPath(import.meta.url));
const projects = [];
const navItems = [
  ['home', 'home', '首页'], ['inbox', 'inbox', '通知'],
  ['explore', 'search', '探索'], ['store', 'shopping_bag', '商店'], ['profile', 'person', '我的']
];
const go = (to, transition = 'slide') => ({ to, transition });

function project(key, title, brief) {
  const doc = {
    title: `Etoile · ${title}`, brief: `${brief} 示例数据仅用于布局。Nothing 是默认风格；本画布专门设计可切换的 Material 3 Expressive 模式，Miuix 另行保留。`,
    frame: 'phone', platform: 'android', paletteKey: 'purple',
    theme: { dark: false, bothModes: true, contrast: 'standard', shape: 'rounded', font: 'robotoFlex', emphasized: true, motion: 'expressive' },
    frames: [], groups: []
  };
  projects.push({ key, doc });
  return doc;
}

function screen(doc, id, name, note, { root = false, selected = 0, wide = false, action, icon2 } = {}) {
  const index = doc.frames.length;
  const frame = wide
    ? { id, name, x: 0, y: (index + 1) * 972, w: 1280, h: 800, note }
    : { id, name, x: (index % 4) * 492, y: Math.floor(index / 4) * 972, note };
  doc.frames.push(frame);
  let sequence = 0;
  function part(kind, x, y, label = '', extra = {}) {
    const suffix = ++sequence;
    const item = { id: `${id}-item-${suffix}`, kind, label, icon: null, variant: 'filled', ...extra };
    doc.groups.push({ id: `${id}-group-${suffix}`, x: frame.x + x, y: frame.y + y, axis: 'x', items: [item] });
    return item;
  }
  function list(y, rows, { x = 16, width = 380 } = {}) {
    const suffix = ++sequence;
    const items = rows.map(([label, supporting, icon, to], i) => ({
      id: `${id}-row-${suffix}-${i}`, kind: 'listItem', label, supporting,
      icon: icon ?? null, variant: 'filled', size: width,
      ...(to ? { icon2: 'chevron_right', action: go(to) } : {})
    }));
    doc.groups.push({ id: `${id}-group-${suffix}`, x: frame.x + x, y: frame.y + y, axis: 'y', items });
  }
  function nav() {
    part(wide ? 'navRail' : 'bottomNav', 0, wide ? 0 : 788, '', {
      tabs: navItems.map(([, icon, label]) => ({ icon, label })), selected,
      actions: Object.fromEntries(navItems.map(([to], i) => [`tab:${i}`, go(to, 'fade')])),
      ...(wide ? { railExpanded: false, size2: 800 } : {})
    });
  }
  part('topAppBar', wide && root ? 96 : 0, 0, name, {
    icon: root ? null : 'arrow_back', ...(icon2 ? { icon2 } : {}),
    actions: { ...(!root ? { icon: go('back') } : {}), ...(action ? { icon2: go(action) } : {}) },
    ...(wide ? { size: root ? 1184 : 1280 } : {})
  });
  if (root) nav();
  return {
    part, list,
    title: (y, label, x = 16) => part('text', x, y, label, { size: 20, bold: true }),
    caption: (y, label, x = 16) => part('text', x, y, label, { size: 14 }),
    card: (y, label, supporting, extra = {}) => part('card', extra.x ?? 16, y, label, {
      supporting, noImage: true, size: 380, size2: 112, ...extra
    }),
    button: (y, label, to, extra = {}) => part('button', extra.x ?? 16, y, label, {
      size: 380, ...(to ? { action: go(to) } : {}), ...extra
    }),
    field: (y, label, extra = {}) => part('textField', 16, y, label, { variant: 'outlined', size: 380, ...extra }),
    tabs: (y, labels, selected = 0) => part('tabs', 0, y, '', { tabs: labels.map(label => ({ label })), selected }),
    search: (y, label) => part('searchBar', 16, y, label, { size: 380 }),
    filter: (y, labels) => {
      const suffix = ++sequence;
      doc.groups.push({ id: `${id}-group-${suffix}`, x: frame.x + 16, y: frame.y + y, axis: 'x', items: labels.map((label, i) => ({
        id: `${id}-chip-${suffix}-${i}`, kind: 'chip', label, icon: null, variant: 'filled', checked: i === 0
      })) });
    }
  };
}

const main = project('workspace', '主页面', '五个主导航目的地；首页先处理工作，探索和商店先搜索，个人页先展示身份及账户入口。');
const home = screen(main, 'home', '首页', '四个工作入口按任务组织。手机两列，窄屏或大字体一列，宽屏四列。其余工作入口保留在同一滚动区域。', { root: true, icon2: 'settings', action: 'settings' });
home.caption(104, '你的工作与收藏，一目了然');
home.title(136, '我的工作');
[['我的 Issue', 'radio_button_checked', 'primaryContainer', 'my-issues'], ['我的 PR', 'call_split', 'secondaryContainer', 'my-pulls'], ['仓库', 'folder', 'surfaceContainerHigh', 'my-repositories'], ['星标收藏', 'star', 'tertiaryContainer', 'my-stars']].forEach(([label, icon, fill, to], i) => {
  home.card(176 + Math.floor(i / 2) * 124, label, '', { x: 16 + (i % 2) * 196, size: 184, size2: 112, icon, fill, action: go(to), note: '打开对应工作列表；实际数据由当前账号提供。' });
});
home.list(436, [['组织', '查看你的组织', 'public'], ['讨论', '在 GitHub 打开', 'forum'], ['项目', '在 GitHub 打开', 'dashboard']]);
home.card(672, '贡献记录', '按日期查看项目贡献', { size2: 96, fill: 'surfaceContainerLow' });

const inbox = screen(main, 'inbox', '通知', '筛选、批量操作与通知共享纵向滚动。未读由圆点和字重共同表达。更多操作打开菜单，取消订阅先确认。', { root: true, selected: 1 });
inbox.caption(104, '跟进提及、审查和项目动态');
inbox.filter(144, ['全部', '提及我', '审查请求']);
inbox.title(200, '最近动态');
inbox.button(236, '全部标为已读', null, { variant: 'tonal', note: '没有未读通知或操作进行中时禁用。' });
inbox.list(312, [['修复窄屏下的导航布局', 'Etoile / android · PR · 5 分钟前', 'circle'], ['请审查新的深色配色', 'Etoile / design · Issue · 20 分钟前', 'alternate_email'], ['Material 3 Expressive 更新', 'lnkiai / m3e-canvas · Release', 'new_releases']]);

const explore = screen(main, 'explore', '探索', '搜索范围始终可见。搜索、主题筛选、结果共用滚动容器；页面翻转模式与结果列表共享同一份搜索状态。', { root: true, selected: 2 });
explore.search(104, '搜索 GitHub');
explore.button(176, '仓库', null, { size: 324, icon: 'expand_more', variant: 'tonal', note: '选择仓库、用户、代码、Issue 或 PR。文字占剩余宽度，窄屏可换行；右侧翻页按钮固定 48dp。' });
explore.part('iconButton', 348, 176, '翻页浏览', { icon: 'auto_stories', variant: 'tonal', note: '仅仓库搜索显示，切换后保留查询。' });
explore.filter(248, ['热门', 'Android', 'Kotlin']);
explore.title(304, '发现开源项目');
explore.list(348, [['lnkiai / m3e-canvas', '拼装 Material 3 Expressive 界面 · TypeScript', 'folder'], ['Etoile / android', '把你的 GitHub 工作带到 Android', 'folder'], ['android / compose-samples', 'Jetpack Compose 示例', 'folder']]);

const store = screen(main, 'store', '商店', '搜索与目录选择随内容滚动。推荐应用按可读宽度组成网格；F-Droid 的加载和错误状态也保留切换入口。', { root: true, selected: 3 });
store.search(104, '搜索开源 Android 应用');
store.filter(176, ['推荐', 'F-Droid']);
store.list(228, [['应用来源', '管理用于发现应用的仓库', 'source', 'sources']]);
store.title(320, '发现应用');
[['Etoile', 'GitHub 工作空间'], ['AntennaPod', '播客播放器'], ['Fossify Gallery', '照片与相册'], ['Organic Maps', '离线地图']].forEach(([name, body], i) => store.card(364 + Math.floor(i / 2) * 164, name, body, { x: 16 + (i % 2) * 196, size: 184, size2: 148, icon: 'android' }));

const profile = screen(main, 'profile', '我的', '身份、统计与账户入口位于贡献和 README 之前。宽屏将身份与活动分栏，大字体回到单栏。', { root: true, selected: 4, icon2: 'settings', action: 'settings' });
profile.card(104, 'Etoile 开发者', '@etoile · 构建好用的开源工具', { icon: 'person', size2: 124, fill: 'primaryContainer' });
profile.card(244, '42 仓库 · 128 关注者', '32 正在关注', { size2: 88, fill: 'surfaceContainerLow' });
profile.title(352, '账户与工作');
profile.list(396, [['管理账户', '切换或添加 GitHub 账户', 'manage_accounts', 'accounts'], ['仓库与收藏', '继续你的项目', 'folder', 'my-repositories'], ['组织', '查看你的协作空间', 'public']]);
profile.card(632, '贡献与成就', '下方继续浏览贡献图、成就与个人 README', { size2: 124 });

function settingsScreens(doc) {
  const settings = screen(doc, 'settings', '设置', '外观与语言各有独立页面。入口显示当前选择；返回保留选择与导航位置。');
  settings.caption(104, '外观与语言');
  settings.list(152, [
    ['外观', 'Material 3 Expressive · 跟随系统 · 默认配色', 'palette', 'appearance'],
    ['语言', '跟随系统', 'language', 'language']
  ]);
  const appearance = screen(doc, 'appearance', '外观', '设计风格、明暗模式与配色在同一页面。选择立即预览；Nothing 保持默认并固定单色，M3E 与 Miuix 支持配色。全页滚动，大字体的配色网格改为单列。');
  appearance.card(104, 'Material 3 Expressive', '设计风格、明暗模式和配色', { fill: 'primaryContainer', size2: 80 });
  appearance.title(208, '设计风格');
  appearance.list(244, [['Nothing', '默认主视觉', 'radio_button_unchecked'], ['Material 3 Expressive', '当前预览', 'radio_button_checked'], ['Miuix', '独立视觉风格', 'radio_button_unchecked']]);
  appearance.title(488, '明暗模式');
  appearance.filter(524, ['跟随系统', '浅色', '深色']);
  appearance.title(584, '配色');
  ['默认', '海洋蓝', '日落橙', '森林绿', '科技紫', 'MIUI 蓝', 'Nothing'].forEach((label, i) => {
    appearance.button(620 + Math.floor(i / 2) * 64, label, null, {
      x: 16 + (i % 2) * 196, size: 184, variant: i === 0 ? 'tonal' : 'outlined',
      note: '在原生应用中立即应用配色，并更新页面顶部预览。'
    });
  });
  const language = screen(doc, 'language', '语言', '语言单独成页。原生应用保存后立即应用，重建页面时保留导航栈；使用语言本名方便找回选择。');
  language.caption(104, '选择应用显示语言，更改后立即生效。');
  language.list(160, [
    ['跟随系统', '', 'radio_button_checked'], ['English', '', 'radio_button_unchecked'],
    ['中文', '', 'radio_button_unchecked'], ['Tiếng Việt', '', 'radio_button_unchecked'],
    ['日本語', '', 'radio_button_unchecked'], ['Русский', '', 'radio_button_unchecked']
  ]);
}
settingsScreens(main);
const quickLogin = screen(main, 'signin', '登录 GitHub', '仅在需要身份时要求登录。已有账号可从账户管理切换。');
quickLogin.card(112, '连接你的 GitHub', '同步工作、通知与收藏', { icon: 'person', fill: 'primaryContainer', size2: 160 });
quickLogin.button(304, '通过 GitHub 登录', null, { icon: 'login', note: '打开现有 OAuth 流程。取消后回到当前页面。' });
quickLogin.list(384, [['使用设备代码', '在其他浏览器完成授权', 'devices'], ['使用个人访问令牌', '在本机安全保存', 'key']]);

const wideHome = screen(main, 'home-wide', '首页', '首页的宽窗口布局：96dp 导航栏，四个等宽工作入口；内容最多 1200dp。', { root: true, wide: true, icon2: 'settings', action: 'settings' });
wideHome.caption(108, '你的工作与收藏，一目了然', 120);
['我的 Issue', '我的 PR', '仓库', '星标收藏'].forEach((label, i) => wideHome.card(160, label, '打开工作列表', { x: 120 + i * 284, size: 268, size2: 132, fill: ['primaryContainer', 'secondaryContainer', 'surfaceContainerHigh', 'tertiaryContainer'][i], action: go(['my-issues', 'my-pulls', 'my-repositories', 'my-stars'][i]) }));
wideHome.list(324, [['组织', '查看成员与项目', 'public'], ['讨论', '在 GitHub 打开', 'forum'], ['项目', '在 GitHub 打开', 'dashboard']], { x: 120, width: 1112 });
wideHome.card(572, '贡献记录', '使用完整宽度显示可滚动的贡献日历', { x: 120, size: 1112, size2: 160 });
const wideProfile = screen(main, 'profile-wide', '我的', '同一个人页的双栏布局。账户操作在左，贡献、成就和 README 在右；字体增大后回到单栏。', { root: true, selected: 4, wide: true, icon2: 'settings', action: 'settings' });
wideProfile.card(104, 'Etoile 开发者', '@etoile · 构建好用的开源工具', { x: 120, size: 368, size2: 172, fill: 'primaryContainer', icon: 'person' });
wideProfile.list(300, [['管理账户', '当前账户与其他账户', 'manage_accounts', 'accounts'], ['仓库', '42 个公开仓库', 'folder'], ['星标收藏', '按标签整理', 'star'], ['组织', '协作空间', 'public']], { x: 120, width: 368 });
wideProfile.card(104, '贡献记录', '按日期查看公开贡献', { x: 512, size: 720, size2: 192 });
wideProfile.card(320, '成就', '根据公开数据展示达成状态', { x: 512, size: 720, size2: 124 });
wideProfile.card(468, 'README', '介绍、项目链接和近期活动以正文排版呈现。', { x: 512, size: 720, size2: 240 });

const quickIssues = screen(main, 'my-issues', '我的 Issue', '从首页进入当前账户的议题列表。完整讨论流程见 Issue 与 PR 画布。');
quickIssues.filter(104, ['分配给我', '我创建的', '提及我']);
quickIssues.list(168, [['修复窄屏下的导航布局', 'Etoile / android · #128 · 打开', 'radio_button_checked'], ['保留搜索与筛选状态', 'Etoile / android · #126 · 打开', 'radio_button_checked']]);
const quickPulls = screen(main, 'my-pulls', '我的 PR', '从首页进入当前账户的拉取请求。完整审查流程见 Issue 与 PR 画布。');
quickPulls.filter(104, ['我创建的', '审查请求', '全部']);
quickPulls.list(168, [['完善页面设计', 'Etoile / android · #130 · 等待审查', 'call_split'], ['修复列表恢复', 'Etoile / android · #125 · 已合并', 'merge']]);
const quickRepositories = screen(main, 'my-repositories', '仓库', '仓库搜索与结果共用滚动容器。详情、代码和发布流程见仓库与代码画布。');
quickRepositories.search(104, '搜索你的仓库');
quickRepositories.list(184, [['Etoile / android', 'GitHub 工作空间 · Kotlin', 'folder'], ['Etoile / design', '页面设计与组件', 'folder']]);
const quickStars = screen(main, 'my-stars', '星标收藏', '分类过滤保留在列表顶部。完整标签管理流程见账户与设置画布。');
quickStars.search(104, '搜索星标仓库'); quickStars.filter(176, ['全部', 'Android', '设计']);
quickStars.list(244, [['lnkiai / m3e-canvas', '设计 · TypeScript', 'star'], ['android / compose-samples', 'Android · Kotlin', 'star']]);
const quickAccounts = screen(main, 'accounts', '管理账户', '当前账户有选中标记；添加账户进入登录流程，返回保留原页面。');
quickAccounts.list(112, [['etoile-developer', '当前账户', 'check_circle'], ['etoile-reviewer', '点击切换', 'person']]);
quickAccounts.button(304, '添加账户', 'signin', { icon: 'add' });
const quickSources = screen(main, 'sources', '应用来源', '原生界面以底部面板打开，独立于应用搜索。输入与列表可滚动，键盘弹出后仍可添加来源。');
quickSources.field(112, '仓库地址', { supporting: 'owner/repository 或 GitHub 仓库链接', icon: 'link' });
quickSources.button(216, '添加来源', null, { icon: 'add', note: '空输入或加载时禁用；失败保留输入并显示原因。' });
quickSources.list(304, [['Etoile / android', '用于发现 Android 发布文件', 'source']]);

const repos = project('repositories', '仓库与代码', '仓库身份和操作清晰分层；代码、README、发布说明有稳定的阅读宽度。');
const repo = screen(repos, 'repository', 'Etoile', '所有入口对应真实路由。手机先概览和操作，再 README；宽屏将概览与 README 分开。');
repo.card(104, 'Etoile / android', 'GitHub 工作空间，支持 Nothing、M3E 与 Miuix。', { size2: 128, icon: 'folder', fill: 'primaryContainer' });
repo.button(248, '浏览代码', 'files', { icon: 'code' });
repo.list(320, [['分支与标签', 'main', 'account_tree', 'refs'], ['发布版本', '版本说明与下载', 'new_releases', 'releases'], ['提交记录', '查看最近改动', 'history', 'commits']]);
repo.card(564, 'README', '项目介绍与使用说明；相对链接使用当前仓库解析。', { size2: 172, action: go('reader') });
const files = screen(repos, 'files', '文件', '分支选择和路径面包屑在列表顶部。长路径可横向滚动；二进制与超大文件提供明确的打开方式。');
files.button(104, 'main · 切换分支或标签', 'refs', { variant: 'tonal', icon: 'account_tree' });
files.caption(184, 'Etoile / android /');
files.list(224, [['app', '目录', 'folder', 'files'], ['docs', '目录', 'folder', 'files'], ['README.md', '项目说明', 'description', 'reader'], ['build.gradle', '构建配置', 'code', 'reader']]);
const reader = screen(repos, 'reader', 'README.md', 'Markdown 使用正文尺度；代码支持双向滚动与选择。切换原文和预览不改变当前仓库上下文。');
reader.caption(104, 'Etoile / android · main'); reader.filter(144, ['预览', '原文']);
reader.card(204, 'Etoile', '在 Android 上浏览项目、处理通知、审查代码。\n\n三种可切换的视觉风格，使用同一份账户和仓库数据。', { size2: 260 });
reader.list(488, [['快速开始', '阅读安装与登录说明', 'menu_book'], ['下载最新版本', '打开发布版本', 'download', 'releases']]);
const refs = screen(repos, 'refs', '分支与标签', '两个数据集分别加载、分页和重试。没有匹配时保留搜索与清除入口。');
refs.search(104, '搜索分支或标签'); refs.tabs(176, ['分支', '标签']);
refs.list(248, [['main', '默认分支 · 受保护', 'account_tree', 'files'], ['feature/expressive-layout', '最近更新', 'account_tree', 'files'], ['release/0.1', '发布分支', 'account_tree', 'files']]);
const releases = screen(repos, 'releases', '发布版本', '状态标签区分正式版、预发布和草稿；有权限才展示管理动作。');
releases.caption(104, 'Etoile / android');
releases.list(152, [['Etoile 0.1', '最新发布 · 3 个文件', 'new_releases', 'release'], ['Etoile 0.1 preview', '预发布 · 2 个文件', 'science', 'release']]);
const release = screen(repos, 'release', 'Etoile 0.1', '文件快捷入口跳到下载区。完整文件名、架构、大小与下载动作分行，长名称不能挤走按钮。');
release.caption(104, '正式版 · main'); release.button(144, '查看下载文件', 'assets', { variant: 'tonal', icon: 'download' });
release.card(224, '更新内容', '改进页面分组、宽屏布局与大字体阅读体验。\n\n修复通知、代码和发布页的布局细节。', { size2: 240 });
release.list(492, [['下载文件', '按设备架构选择 APK', 'download', 'assets']]);
const assets = screen(repos, 'assets', '下载文件', '所有名称完整换行；点击下载交给现有下载流程。错误、取消和再次尝试就地反馈。');
assets.list(112, [['Etoile-Android-arm64-v8a.apk', 'arm64-v8a · APK', 'android'], ['Etoile-Android-armeabi-v7a.apk', 'armeabi-v7a · APK', 'android'], ['Source code', 'zip · 源码', 'folder_zip']]);
const commits = screen(repos, 'commits', '提交记录', '提交标题优先，作者、时间和短 SHA 为次级信息。');
commits.list(112, [['完善 M3E 页面布局', 'etoile · 2 小时前 · 1a2b3c4', 'commit', 'commit'], ['修复搜索状态恢复', 'etoile · 昨天 · 5d6e7f8', 'commit', 'commit']]);
const commit = screen(repos, 'commit', '提交详情', '文件名独占一行。补丁可展开、折叠与横向滚动；二进制文件明确说明没有文本差异。');
commit.card(104, '完善 M3E 页面布局', 'etoile · 1a2b3c4 · 4 个文件', { size2: 124 });
commit.list(252, [['GithubNavChrome.kt', '+24 −12 · 查看差异', 'code'], ['ProfileScreen.kt', '+36 −20 · 查看差异', 'code']]);
commit.card(420, '代码差异', '+  自适应导航与内容宽度\n−  固定宽度布局', { size2: 236, fill: 'surfaceContainerLow' });
const wideRepo = screen(repos, 'repository-wide', 'Etoile', '仓库双栏最多 1200dp。左栏概览与操作，右栏 README。大字体时按可读宽度回到单栏。', { wide: true });
wideRepo.card(104, 'Etoile / android', '你的 Android GitHub 工作空间', { x: 32, size: 336, size2: 156, fill: 'primaryContainer' });
wideRepo.list(284, [['浏览代码', 'main', 'code', 'files'], ['分支与标签', '切换上下文', 'account_tree', 'refs'], ['发布版本', '版本说明与文件', 'new_releases', 'releases'], ['提交记录', '最近更改', 'history', 'commits']], { x: 32, width: 336 });
wideRepo.card(104, 'README', '安装、登录与使用说明。保持适合阅读的行长；文档和代码单独滚动。', { x: 400, size: 832, size2: 588 });

const conv = project('conversations', 'Issue 与 PR', '列表按状态筛选，详情先上下文再讨论，编辑与提交保持键盘安全区。');
const work = screen(conv, 'my-work', '我的工作', '在同一账户内查看 Issue 或 PR；筛选和分页状态始终可见。');
work.tabs(104, ['Issue', 'PR']); work.filter(168, ['打开', '已关闭', '全部']);
work.list(228, [['修复窄屏布局', 'Etoile / android · #128', 'radio_button_checked', 'issue'], ['完善页面设计', 'Etoile / android · #130', 'call_split', 'pull']]);
const issueList = screen(conv, 'issues', 'Issue', '创建入口只出现一次。刷新保留已有结果；失败可在当前列表重试。');
issueList.filter(104, ['打开', '已关闭', '全部']); issueList.list(168, [['修复窄屏布局', '#128 · 3 条评论', 'radio_button_checked', 'issue'], ['支持新的筛选条件', '#126 · 1 条评论', 'radio_button_checked', 'issue']]);
issueList.part('extendedFab', 232, 764, '新建 Issue', { icon: 'add', action: go('create') });
const issue = screen(conv, 'issue', 'Issue #128', '状态与作者置于标题之后；标签编辑在独立面板，讨论使用全宽正文。');
issue.card(104, '修复窄屏布局', '打开 · Etoile / android', { size2: 132, fill: 'primaryContainer' });
issue.button(252, '标签与负责人', 'metadata', { variant: 'tonal' });
issue.card(328, '问题描述', '在较窄窗口或系统大字体下，操作应保持可见，文本可以自然换行。', { size2: 176 });
issue.list(532, [['维护者', '感谢反馈，已准备布局修复。', 'person']]); issue.field(632, '发表评论', { note: '提交前校验非空，失败保留输入；需要登录。' });
issue.button(720, '发表评论', null, { icon: 'send' });
const create = screen(conv, 'create', '新建 Issue', '草稿按仓库保存并恢复。标题必填；提交中禁用重复操作。键盘出现时表单与按钮可滚动到可见范围。');
create.caption(104, 'Etoile / android'); create.field(152, '标题', { supporting: '简短描述要处理的问题' });
create.field(244, '描述', { supporting: '支持 Markdown', note: '多行输入，随可用高度增长。' });
create.card(340, '预览', '提交前查看 Markdown 排版', { size2: 176, fill: 'surfaceContainerLow' }); create.button(548, '创建 Issue', 'issue', { note: '实际应用仅在请求成功后跳转。' });
const metadata = screen(conv, 'metadata', '标签与负责人', '在有权限时编辑。选项可滚动，保存过程中禁用；失败保留当前选择。');
metadata.search(104, '搜索标签'); metadata.list(188, [['bug', '问题修复', 'check'], ['enhancement', '功能改进', 'radio_button_unchecked'], ['design', '界面与交互', 'check']]); metadata.button(448, '保存更改', 'back');
const pulls = screen(conv, 'pulls', 'Pull Request', '状态、分支、评论数分层显示，长标题不会挤压审查状态。'); pulls.filter(104, ['打开', '已合并', '全部']); pulls.list(168, [['完善页面设计', '#130 · feature/m3e → main', 'call_split', 'pull'], ['修复列表恢复', '#125 · 已合并', 'merge', 'pull']]);
const pull = screen(conv, 'pull', 'PR #130', '概览、活动、文件按页签组织；审批表单仅在允许审查时可用。'); pull.card(104, '完善页面设计', '打开 · feature/m3e → main', { size2: 124 }); pull.tabs(248, ['概览', '活动', '文件']); pull.card(316, '变更说明', '调整首页工作入口、通知阅读顺序与自适应布局。', { size2: 184 }); pull.button(532, '审查更改', 'review', { icon: 'rate_review' });
const review = screen(conv, 'review', '审查更改', '评论、批准、请求更改各自有明确按钮。草稿与展开状态恢复，失败保留评论；没有真实请求时不能显示成功。'); review.card(104, 'GithubNavChrome.kt', '代码差异可展开和横向滚动', { size2: 216, fill: 'surfaceContainerLow' }); review.field(348, '审查意见', { supporting: '请求更改时说明原因' }); review.button(456, '提交评论', null); review.button(528, '批准', null, { variant: 'tonal' }); review.button(600, '请求更改', null, { variant: 'outlined' });

const widePull = screen(conv, 'pull-wide', 'PR #130 · 宽屏', '文件和活动页可用到 1200dp。达到 840dp × 字体倍数后分栏，摘要宽度随字号增长；不足时回到单栏。概览保持 840dp 阅读上限。', { wide: true });
widePull.card(104, '完善页面设计', '打开 · feature/m3e → main\n3 次提交 · 3 个文件\n+48 −12', { x: 40, size: 380, size2: 200, fill: 'primaryContainer' });
widePull.card(332, '变更说明', '让 PR 审查与 Actions 日志在大字体下保持完整的阅读空间。', { x: 40, size: 380, size2: 208 });
widePull.part('tabs', 452, 104, '', { size: 788, selected: 2, tabs: ['概览', '活动', '文件'].map(label => ({ label })), actions: { 'tab:0': go('pull'), 'tab:1': go('review') } });
widePull.card(184, 'ActionsScreens.kt', '+16 −4 · 代码差异可横向滚动\n\n+  根据系统字号选择单栏或双栏\n+  为长分支与构建日志保留空间', { x: 452, size: 788, size2: 256, fill: 'surfaceContainerLow' });
widePull.card(468, 'PullRequestsScreens.kt', '+16 −4 · 详情与审查区域独立滚动\n切换页签后保留各自的阅读位置。', { x: 452, size: 788, size2: 212, fill: 'surfaceContainerLow' });

const accounts = project('accounts', '账户与设置', '保留三种独立视觉风格，账户与语言操作具有完整标签。');
const signIn = screen(accounts, 'signin', '登录 GitHub', '浏览公开内容无需登录。授权、设备代码和令牌是三种已有入口。'); signIn.card(104, '连接你的 GitHub', '同步仓库、通知与收藏', { size2: 160, fill: 'primaryContainer', icon: 'person' }); signIn.button(288, '通过 GitHub 登录', null, { icon: 'login' }); signIn.list(376, [['使用设备代码', '在浏览器完成授权', 'devices'], ['使用个人访问令牌', '输入后在本机保存', 'key'], ['已有账户', '管理已保存的账户', 'manage_accounts', 'accounts']]);
const account = screen(accounts, 'accounts', '管理账户', '当前账户有明确选中标记。删除需要确认具体账号；旋转设备后确认对象不变。'); account.list(112, [['etoile-developer', '当前账户', 'check_circle'], ['etoile-reviewer', '点击切换', 'person']]); account.button(296, '添加账户', 'signin', { icon: 'add' }); account.caption(376, '移除账户前会再次确认');
settingsScreens(accounts);
const publicProfile = screen(accounts, 'public-profile', '开发者', '公开身份、关注关系和仓库分层展示；长简介自然换行。'); publicProfile.card(104, 'Etoile 开发者', '@etoile · 开源 Android 工具', { size2: 148, icon: 'person', fill: 'primaryContainer' }); publicProfile.button(276, '关注', null, { note: '登录后操作，等待服务端响应再更新状态。' }); publicProfile.list(356, [['关注者', '查看公开关注者', 'group', 'connections'], ['公开仓库', '查看与搜索仓库', 'folder'], ['组织', '查看所属组织', 'public', 'organizations']]);
const connections = screen(accounts, 'connections', '关注者', '使用一致的用户行；分页、空状态和重试出现在列表末尾。'); connections.list(112, [['etoile-developer', 'Android 与开源工具', 'person', 'public-profile'], ['design-maintainer', 'Material 设计与组件', 'person', 'public-profile']]);
const orgs = screen(accounts, 'organizations', '组织', '组织名称优先，说明次之；打开组织的公开资料。'); orgs.list(112, [['Etoile Community', '开源工具与协作', 'public', 'public-profile'], ['Android Developers', 'Android 项目', 'public', 'public-profile']]);
const stars = screen(accounts, 'stars', '星标收藏', '标签过滤、管理与批量选择有明确入口。批量操作期间显示数量并保留选择。'); stars.search(104, '搜索星标仓库'); stars.filter(176, ['全部', 'Android', '设计']); stars.button(232, '管理标签', 'labels', { variant: 'tonal', icon: 'label' }); stars.list(316, [['lnkiai / m3e-canvas', '设计 · TypeScript', 'star'], ['android / compose-samples', 'Android · Kotlin', 'star']]);
const labels = screen(accounts, 'labels', '管理标签', '长标签列表独立滚动，新增字段和保存按钮始终能到达。删除与重命名就地反馈。'); labels.list(112, [['Android', '12 个仓库', 'label'], ['设计', '8 个仓库', 'label'], ['稍后阅读', '5 个仓库', 'label']]); labels.field(360, '新标签名称'); labels.button(448, '创建标签', null, { icon: 'add' });

const ops = project('operations', '自动化与应用', '运行状态和可执行操作分开；下载、日志与仓库管理内容有清晰反馈。');
const workflows = screen(ops, 'workflows', 'Actions', '工作流标题独占可读宽度，路径为次级信息；运行和手动触发不挤在标题一行。'); workflows.list(112, [['Android Build', '.github/workflows/android.yml', 'play_circle', 'runs'], ['Release', '.github/workflows/release.yml', 'play_circle', 'runs']]);
const runs = screen(ops, 'runs', '工作流运行', '状态、分支和触发者完整可读；长分支换行。'); runs.filter(104, ['全部', '成功', '失败']); runs.list(168, [['Build #108', '失败 · feature/m3e · etoile', 'error', 'run'], ['Build #107', '成功 · main · etoile', 'check_circle', 'run']]);
const run = screen(ops, 'run', 'Build #108', '运行摘要先于重新运行等操作；权限不足时不显示管理按钮。'); run.card(104, '构建失败', 'feature/m3e · 手动触发 · 2 分钟', { size2: 132 }); run.button(260, '重新运行', null, { variant: 'tonal', icon: 'replay', note: '请求成功后刷新状态；失败明确显示。' }); run.title(344, '作业'); run.list(388, [['assembleDebug', '失败 · 1 分 24 秒', 'error', 'job'], ['unitTests', '成功 · 36 秒', 'check_circle', 'job']]);
const job = screen(ops, 'job', '作业日志', '步骤名称全宽，状态在名称下方。日志可切换自动换行或横向滚动，截断必须说明。'); job.list(104, [['Checkout', '成功', 'check_circle'], ['Assemble debug', '失败', 'error']]); job.button(272, '自动换行', null, { variant: 'tonal', icon: 'wrap_text', toggle: { label: '横向滚动', icon: 'swap_horiz', variant: 'filled' } }); job.card(352, '构建日志', '> Task :app:assembleDebug\n完整日志可选择、滚动或在 GitHub 打开。', { size2: 288, fill: 'surfaceContainerLow' });
const app = screen(ops, 'app', 'Etoile', '仓库身份、发布版本与可安装文件分组。明确设备兼容性；下载完成后由系统安装器处理安装。'); app.card(104, 'Etoile', 'Android GitHub 工作空间', { icon: 'android', fill: 'primaryContainer', size2: 148 }); app.list(280, [['最新版本', '0.1 · 查看发布说明', 'new_releases'], ['arm64-v8a.apk', '适合当前设备 · 下载 APK', 'download']]); app.button(456, '下载 APK', null, { icon: 'download' }); app.part('linearProgress', 16, 540, '', { value: 42, wavy: true }); app.caption(588, '下载进度与取消操作就地显示');
const sources = screen(ops, 'sources', '应用来源', '来源名称和地址分行。新增或移除只影响应用目录，失败保留输入。'); sources.list(112, [['推荐仓库', 'GitHub 公开 Android 项目', 'source'], ['F-Droid', '开源应用索引', 'android']]); sources.field(304, '仓库地址', { supporting: 'owner/repository 或 GitHub 仓库链接' }); sources.button(400, '添加来源', null, { icon: 'add' });
const collaborators = screen(ops, 'collaborators', '协作者', '账户与角色分层展示。编辑只在拥有权限时出现；保存反馈属于具体用户。'); collaborators.list(112, [['etoile-maintainer', '管理员', 'person'], ['etoile-reviewer', '写入权限', 'person']]); collaborators.card(296, '角色与权限', '在可编辑时选择角色并保存。受限账户显示只读内容。', { size2: 148 });
const webhooks = screen(ops, 'webhooks', 'Webhooks', '目前原生页只读；管理按钮在 GitHub 打开指定 Webhook。相同名称用 ID 区分。'); webhooks.card(104, 'web · #108', '已启用 · push / pull_request\n最近响应：503 Service Unavailable', { size2: 184 }); webhooks.button(312, '在 GitHub 管理', null, { variant: 'tonal', icon: 'open_in_new', note: '打开当前仓库的 /settings/hooks/108；不伪装为原生编辑。' }); webhooks.card(400, 'web · #109', '已停用 · issues\n最近响应：200 OK', { size2: 164 });

const wideRun = screen(ops, 'run-wide', 'Build #108 · 宽屏', '最大内容宽度 1200dp。只有可用宽度达到 840dp × 字体倍数才将摘要和作业分栏；大字体时回到同一滚动列表。', { wide: true });
wideRun.card(104, '验证详情布局与构建日志', '成功 · feature/adaptive-detail-reading\n触发者：android-release-maintainer\n3 分钟 · pull_request', { x: 40, size: 380, size2: 248, fill: 'primaryContainer' });
wideRun.title(112, '作业', 452);
wideRun.list(172, [['Build Android preview', '成功 · 3 分钟 · 查看步骤与日志', 'check_circle', 'job-wide']], { x: 452, width: 788 });
const wideJob = screen(ops, 'job-wide', '作业日志 · 宽屏', '摘要与步骤在左侧，日志在右侧。侧栏随字体放大；没有足够阅读宽度时合为单栏，日志末尾仍可滚动到达。', { wide: true });
wideJob.card(104, 'Build Android preview', '成功 · android-release-runner-arm64\nubuntu-latest · arm64', { x: 40, size: 400, size2: 172, fill: 'primaryContainer' });
wideJob.list(304, [['Check out source', '成功', 'check_circle'], ['Compile Android application', '成功', 'check_circle'], ['Verify layouts', '成功', 'check_circle']], { x: 40, width: 400 });
wideJob.button(104, '自动换行', null, { x: 472, size: 240, variant: 'tonal', icon: 'wrap_text', toggle: { label: '横向滚动', icon: 'swap_horiz', variant: 'filled' } });
wideJob.card(184, '构建日志', '> Task :app:assembleDebug\n> Task :app:testDebugUnitTest\n> Task :app:lintDebug\n\nBUILD SUCCESSFUL', { x: 472, size: 768, size2: 504, fill: 'surfaceContainerLow' });

mkdirSync(directory, { recursive: true });
const manifest = [];
for (const { key, doc } of projects) {
  // Keep wide references beside the phone grid instead of leaving empty rows.
  let phoneIndex = 0;
  let wideIndex = 0;
  for (const frame of doc.frames) {
    const wide = frame.w === 1280;
    const index = wide ? wideIndex++ : phoneIndex++;
    const x = wide ? 4 * 492 : (index % 4) * 492;
    const y = wide ? index * 880 : Math.floor(index / 4) * 972;
    for (const group of doc.groups) {
      if (group.id.startsWith(`${frame.id}-group-`)) {
        group.x += x - frame.x;
        group.y += y - frame.y;
      }
    }
    frame.x = x;
    frame.y = y;
  }
  const json = JSON.stringify(doc, null, 2) + '\n';
  writeFileSync(join(directory, `${key}.json`), json, 'utf8');
  const link = 'https://lnkiai.github.io/m3e-canvas/#docz=' + deflateRawSync(Buffer.from(json)).toString('base64url');
  manifest.push({ key, title: doc.title, frames: doc.frames.length, bytes: Buffer.byteLength(json), link });
}
writeFileSync(join(directory, 'links.json'), JSON.stringify(manifest, null, 2) + '\n');
const cards = manifest.map(({ key, title, frames, link }) => `<article><p class="eyebrow">${frames} 个画面 · 浅色 / 深色</p><h2>${title.replace('Etoile · ', '')}</h2><p>${projects.find(p => p.key === key).doc.brief.split(' 示例数据')[0]}</p><div class="actions"><a class="button" href="${link}" target="_blank" rel="noopener">在 M3E Canvas 打开 ↗</a><a href="${key}.json" download>下载 JSON</a></div></article>`).join('\n');
const previews = [
  ['设置、外观与语言', 'settings'], ['详情页适配', 'adaptive-details'],
  ['宽屏 PR 文件', 'wide-pull-request'], ['宽屏运行详情', 'wide-actions-run'], ['宽屏作业日志', 'wide-actions-job'],
  ['浅色主页面', 'main-light'], ['深色主页面', 'main-dark'],
  ['宽屏首页', 'wide-home'], ['宽屏个人页', 'wide-profile'], ['宽屏仓库', 'wide-repository'],
  ['三种视觉风格', 'three-styles'], ['窄屏探索', 'narrow-explore'], ['大字体语言页', 'narrow-language'], ['大字体配色列表', 'narrow-palette'],
  ['键盘下的表单', 'keyboard-create-issue'], ['应用来源编辑', 'source-editor'], ['阅读与账户', 'secondary-pages']
].map(([label, file]) => `<a href="previews/${file}.webp">${label} ↗</a>`).join('');
writeFileSync(join(directory, 'index.html'), `<!doctype html>
<html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Etoile · M3E 页面设计</title>
<style>:root{color-scheme:light dark;--bg:#fffbfe;--surface:#f3edf7;--ink:#1c1b1f;--muted:#625b71;--primary:#6750a4;--on:#fff}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.65 system-ui,sans-serif}main{max-width:1120px;margin:auto;padding:56px 24px}header{max-width:760px;margin-bottom:36px}.eyebrow{color:var(--primary);font-size:14px;font-weight:650}h1{font-size:clamp(32px,5vw,52px);line-height:1.12;letter-spacing:-1px;margin:12px 0 20px}h2{font-size:24px;margin:8px 0}p{color:var(--muted)}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,420px),1fr));gap:20px}article{background:var(--surface);border-radius:28px;padding:28px;display:flex;flex-direction:column}article>p:not(.eyebrow){flex:1}.actions{display:flex;align-items:center;flex-wrap:wrap;gap:20px;margin-top:12px}a{color:var(--primary);text-underline-offset:4px}.button{display:inline-flex;min-height:48px;align-items:center;background:var(--primary);color:var(--on);padding:12px 20px;border-radius:24px;text-decoration:none}a:focus-visible{outline:3px solid var(--primary);outline-offset:4px}footer{margin-top:32px;color:var(--muted);font-size:14px}@media(prefers-color-scheme:dark){:root{--bg:#141218;--surface:#211f26;--ink:#e6e0e9;--muted:#cac4d0;--primary:#d0bcff;--on:#381e72}}</style>
<style>.previews{margin-top:56px}.preview-image{display:block;width:100%;height:auto;border-radius:24px;margin:24px 0}.preview-links{display:flex;flex-wrap:wrap;gap:8px 24px}.preview-links a{display:inline-flex;align-items:center;min-height:48px}</style>
<main><header><p class="eyebrow">ETOILE / MATERIAL 3 EXPRESSIVE</p><h1>每个页面，都围绕当前任务。</h1><p>五组可编辑画布，覆盖主导航、仓库、讨论、账户、自动化和应用。打开后可以查看页面连接、点击预览、调整配色和导出设计提示词。</p><p><strong>Nothing 仍是默认主视觉。</strong>这些画布专门用于 Etoile 的 M3E 模式，Miuix 保持独立。示例账号、计数和版本不代表真实数据。</p></header><section class="grid" aria-label="可编辑画布">${cards}</section><section class="previews" aria-labelledby="preview-title"><p class="eyebrow">原生 ANDROID 页面</p><h2 id="preview-title">PR 和 Actions，随字号调整布局。</h2><p>宽屏并排查看摘要与内容；窗口变窄或字体放大后恢复单栏。长用户名与文件路径保持可读。以下是 Android 模拟器中的实际布局，使用本地示例数据。</p><a href="previews/adaptive-details.webp"><img class="preview-image" src="previews/adaptive-details.webp" alt="M3E 的 PR 文件与 Actions 日志在宽屏和 320dp 大字体下的实际布局" loading="lazy"></a><div class="preview-links">${previews}</div></section><section class="previews" aria-labelledby="templates-title"><p class="eyebrow">ISSUE 模板</p><h2 id="templates-title">选好模板，再开始填写。</h2><p>内置问题反馈、功能建议和使用求助三套模板，可切换中文与 English。支持仓库 Markdown 与 YAML 表单、必填校验、正文预览和失败重试。没有仓库模板时也可使用内置模板；仓库要求专用模板时遵守其设置。</p><a href="../issue-templates/bilingual-templates.webp"><img class="preview-image" src="../issue-templates/bilingual-templates.webp" alt="中英文内置模板选择页，以及 Nothing 风格的功能建议表单" loading="lazy"></a><div class="preview-links"><a href="../ISSUE_TEMPLATES.md">模板功能说明 ↗</a><a href="../issue-templates/VERIFICATION.md">模板验证记录 ↗</a><a href="../../../app/build/outputs/apk/debug/Etoile-Android-arm64-v8a-0.1.0-26091701-11.APK" download>下载调试 APK · arm64 ↗</a><a href="../../../app/build/outputs/apk/debug/Etoile-Android-armeabi-v7a-0.1.0-26091701-11.APK" download>下载调试 APK · armv7 ↗</a></div><p>安装包为本地调试构建 11。截图及发布流程使用本地模拟仓库，未向真实 GitHub 仓库发布测试议题。</p></section><section class="previews" aria-labelledby="wide-title"><h2 id="wide-title">平板、分屏与大字体</h2><p>列表按空间分列，详情页的摘要与正文并排显示。窗口变窄后恢复单栏，并保留编辑内容。</p><div class="preview-links"><a href="../large-screen/VERIFICATION.md">最新大屏验收记录 ↗</a><a href="../large-screen/responsive-1.jpg">响应式页面预览 ↗</a><a href="../large-screen/themes-1.jpg">三种外观预览 ↗</a></div></section><footer>基于 <a href="https://github.com/lnkiai/m3e-canvas">lnkiai/m3e-canvas</a> 的公开文档格式。也可在编辑器用“打开项目”载入 JSON。<br>画布为布局和导航规格；原生实现与验证记录见 <a href="../M3E_LAYOUT.md">设计说明</a> 和 <a href="VERIFICATION.md">验证记录</a>。</footer></main></html>`, 'utf8');
console.log(manifest.map(({ key, frames, bytes }) => `${key}: ${frames} frames, ${bytes} bytes`).join('\n'));
