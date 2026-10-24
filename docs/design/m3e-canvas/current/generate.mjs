// Current Etoile flows, redrawn from the production Compose screens on 2026-10-21.
// Editable M3E Canvas documents, not screenshots of the Android implementation.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateRawSync } from 'node:zlib';
const out = dirname(fileURLToPath(import.meta.url));
mkdirSync(out, { recursive: true });
const projects = [];
const nav = [['home', 'home', '首页'], ['inbox', 'inbox', '通知'], ['explore', 'search', '探索'], ['store', 'shopping_bag', '商店'], ['profile', 'person', '我的']];
const go = to => ({ to, transition: to === 'back' ? 'slide' : 'slide' });
function project(key, title, brief) {
  const doc = { title: `Etoile · ${title}`, brief: `${brief} 基于2026-10-21当前功能优化。示例内容不是实时账户数据。`, frame: 'phone', platform: 'android', paletteKey: 'purple', theme: { dark: true, bothModes: true, contrast: 'standard', shape: 'rounded', font: 'robotoFlex', emphasized: true, motion: 'expressive' }, frames: [], groups: [] };
  projects.push({ key, doc }); return doc;
}
function screen(doc, id, name, note, { root = false, selected = 0, wide = false, trailing = null, target = null } = {}) {
  const n = doc.frames.length, x = (n % 4) * 1420, y = Math.floor(n / 4) * 1000;
  doc.frames.push({ id, name, x, y, ...(wide ? { w: 1280, h: 800 } : {}), bg: 'surface', note });
  let serial = 0;
  function part(kind, px, py, label = '', extra = {}) {
    const k = `${id}-${++serial}`;
    const item = { id: `${k}-item`, kind, label, icon: null, variant: 'filled', ...extra };
    doc.groups.push({ id: k, x: x + px, y: y + py, axis: 'x', items: [item] }); return item;
  }
  function run(axis, px, py, items) {
    doc.groups.push({ id: `${id}-${++serial}`, x: x + px, y: y + py, axis, items: items.map((item, i) => ({ id: `${id}-${serial}-item-${i}`, icon: null, variant: 'filled', ...item })) });
  }
  const margin = wide ? 120 : 16, width = wide ? 1112 : 380;
  part('topAppBar', wide && root ? 96 : 0, 0, name, { icon: root ? null : 'arrow_back', icon2: trailing, actions: { ...(!root ? { icon: go('back') } : {}), ...(target ? { icon2: go(target) } : {}) }, ...(wide ? { size: root ? 1184 : 1280 } : {}) });
  if (root) part(wide ? 'navRail' : 'bottomNav', 0, wide ? 0 : 788, '', { tabs: nav.map(([, icon, label]) => ({ icon, label })), selected, actions: Object.fromEntries(nav.map(([to], i) => [`tab:${i}`, { to, transition: 'fade' }])), ...(wide ? { railExpanded: false, size2: 800 } : {}) });
  return {
    part, run,
    text: (py, label, extra = {}) => part('text', extra.x ?? margin, py, label, { size: 14, ...extra }),
    title: (py, label, extra = {}) => part('text', extra.x ?? margin, py, label, { size: 20, bold: true, ...extra }),
    card: (py, label, supporting, extra = {}) => part('card', extra.x ?? margin, py, label, { supporting, noImage: true, size: width, size2: 104, fill: 'surfaceContainerLow', ...extra }),
    button: (py, label, to, extra = {}) => part('button', extra.x ?? margin, py, label, { size: width, ...(to ? { action: go(to) } : {}), ...extra }),
    search: (py, label) => part('searchBar', margin, py, label, { size: width }),
    field: (py, label, extra = {}) => part('textField', margin, py, label, { variant: 'outlined', size: width, ...extra }),
    filters: (py, labels, selected = 0) => run('x', margin, py, labels.map((label, i) => ({ kind: 'chip', label, checked: i === selected }))),
    list: (py, rows, extra = {}) => run('y', extra.x ?? margin, py, rows.map(([label, supporting = '', icon = null, to = null]) => ({ kind: 'listItem', label, supporting, icon, size: extra.width ?? width, ...(to ? { icon2: 'chevron_right', action: go(to) } : {}) }))),
    tabs: (py, labels, selected = 0) => part('tabs', 0, py, '', { tabs: labels.map(label => ({ label })), selected })
  };
}
// Explicit parts keep icon/task and left-aligned identity layouts faithful to Compose.
function identities(s, y, rows) {
  rows.forEach(([name, detail, icon, to], i) => {
    const py = y + i * 92;
    s.card(py, '', '', { size2: 84, action: go(to) });
    s.part('iconButton', 28, py + 18, '', { icon, variant: 'tonal', action: go(to) });
    s.text(py + 22, name, { x: 84, size: 14, bold: true, action: go(to) });
    s.text(py + 48, detail, { x: 84, size: 14, action: go(to) });
  });
}
function heatmap(s, x, y, columns = 22, step = 16) {
  for (let row = 0; row < 7; row++) s.run('x', x, y + row * step, Array.from({length:columns}, (_, col) => ({
    kind:'box', label:'', size:step - 3, size2:step - 3, radiusTop:3, radiusBottom:3,
    fill:['surfaceContainerHighest','primaryContainer','secondaryContainer','primary'][(col * 13 + row * 7 + col * row) % 4]
  })));
}
const workspace = project('workspace', '工作空间', '保留当前五个主导航。首页保留紧凑工作列表，通知跟进变化，探索和商店搜索内容，我的管理身份。');
const home = screen(workspace, 'home', '首页', '遵照用户反馈保留现有七行紧凑工作入口、顺序与功能，不使用四格任务卡片。贡献和收藏继续向下滚动。', { root: true, trailing: 'settings', target: 'settings' });
home.text(104, '继续你的开源工作'); home.title(144, '我的工作');
const homeRows = [['星标收藏', '', 'star', 'stars'], ['仓库', '42', 'folder', 'repositories'], ['组织', '', 'group', 'organizations'], ['我的 Issue', '', 'radio_button_checked', 'my-issues'], ['我的 PR', '', 'call_split', 'my-pulls'], ['讨论', 'GitHub 网页', 'forum'], ['项目', 'GitHub 网页', 'dashboard']];
home.list(184, homeRows);
home.button(724, '贡献与收藏 ↓', 'home-continuation', { variant: 'text' });
const continuation = screen(workspace, 'home-continuation', '首页 · 下半部分', '画布固定视口，以续页表达首页向下滚动后的内容，原生保持一个滚动区域。', {root:true});
continuation.title(112, '贡献记录'); continuation.card(160, '', '', {size2:232}); continuation.text(180, '过去一年的贡献 · 128 次', {x:32}); continuation.text(220, '7月                    8月                    9月', {x:32,size:12}); heatmap(continuation,32,252,22,16); continuation.text(384, '最新日期先显示 · 向左查看历史', {x:32,size:12}); continuation.title(440,'收藏的项目'); continuation.list(492,[['android / compose-samples','Kotlin · 24.8k ★','folder','repositories'],['lnkiai / m3e-canvas','TypeScript · 设计','folder','repositories']]);
const inbox = screen(workspace, 'inbox', '通知', '筛选与列表共滚；更多操作留在每行末尾，取消订阅仍要确认。', { root: true, selected: 1 });
inbox.filters(104, ['全部', '提及我', '审查请求']); inbox.title(168, '最近动态');
inbox.part('button', 244, 160, '全部已读', { variant: 'text', size: 152 });
inbox.list(224, [['修复 Markdown 图片排版', 'Etoile / android · 审查请求 · 5分钟前', 'rate_review'], ['跟进 GitLab 自建实例登录', 'Etoile / android · 提及 · 18分钟前', 'alternate_email'], ['Android 版本已发布', 'Etoile / android · Release · 昨天', 'new_releases']]);
const explore = screen(workspace, 'explore', '探索', '搜索范围可见，结果优先；保留已有仓库翻页模式。', { root: true, selected: 2 });
explore.search(104, '搜索仓库、用户、代码…'); explore.filters(176, ['仓库', '用户', '代码', 'Issue', 'PR']); explore.filters(224, ['热门', 'Android', 'Kotlin']);
explore.title(284, '发现项目'); explore.list(332, [['android / compose-samples', 'Jetpack Compose 示例 · Kotlin · 24.8k ★', 'folder', 'repositories'], ['lnkiai / m3e-canvas', 'Material 3 Expressive 设计工具 · TypeScript', 'folder'], ['Etoile / android', 'GitHub 与 GitLab 工作空间 · Kotlin', 'folder']]);
const store = screen(workspace, 'store', '商店', '推荐与F-Droid目录使用同一内容顺序：搜索、目录、结果。', { root: true, selected: 3 });
store.search(104, '搜索开源 Android 应用'); store.filters(176, ['推荐', 'F-Droid']); store.list(228, [['应用来源', '管理用于发现应用的仓库', 'source', 'sources']]);
[['AntennaPod', '播客播放器'], ['Organic Maps', '离线地图'], ['Fossify Gallery', '照片与相册'], ['Etoile', '代码托管客户端']].forEach(([label, supporting], i) => store.card(332 + Math.floor(i / 2) * 172, label, supporting, { x: 16 + (i % 2) * 196, size: 184, size2: 156, icon: 'android', action: go('app') }));
const profile = screen(workspace, 'profile', '我的', '身份与统计一体化；统计横排且各自可点击，账户操作简洁分组。', { root: true, selected: 4, trailing: 'settings', target: 'settings' });
profile.card(104, 'Etoile 开发者', '@etoile · Android 与开源工具', { icon: 'person', fill: 'primaryContainer', size2: 116 });
profile.run('x', 16, 236, [['42 仓库', 'repositories'], ['128 关注者', 'followers'], ['32 已关注', 'following']].map(([label, to]) => ({ kind: 'button', label, size: 126, variant: 'tonal', action: go(to) })));
profile.list(316, [['GitHub 账户', '切换或添加账户', 'manage_accounts', 'accounts'], ['GitLab 账户', 'gitlab.com 或自建实例', 'hub', 'gitlab'], ['星标收藏', '', 'star', 'stars'], ['组织', '', 'group', 'organizations']]);
profile.title(632, '贡献与成就'); profile.card(680, '贡献记录', '最新日期先显示 · 向左查看历史', { size2: 88 });
const wide = screen(workspace, 'home-wide', '首页 · 宽屏', '首页继续沿用现有紧凑工作列表，不改为任务卡片。贡献与收藏在同一滚动区域下方。', { root: true, wide: true, trailing: 'settings', target: 'settings' });
wide.title(104, '我的工作'); wide.list(160, homeRows, {x:120,width:1112}); wide.button(704,'贡献与收藏 ↓','home-continuation-wide',{variant:'text'});
const wideContinuation = screen(workspace, 'home-continuation-wide', '首页下半部分 · 宽屏', '画布续页表示向下滚动后的贡献与收藏，原生不新增分页或按钮。', {root:true,wide:true});
wideContinuation.card(112, '贡献记录', '过去一年的贡献 · 128 次', { x: 120, size: 544, size2: 256 }); heatmap(wideContinuation,144,208,28,17); wideContinuation.text(338,'最新日期先显示 · 向左查看历史',{x:144,size:12}); wideContinuation.title(124,'收藏的项目',{x:688}); wideContinuation.list(176,[['android / compose-samples','Kotlin · 24.8k ★','folder','repositories'],['lnkiai / m3e-canvas','TypeScript · 设计','folder','repositories']],{x:688,width:544});
// Workspace destinations also make the prototype's main navigation close without dead links.
for (const [id, title, rows] of [
  ['my-issues', '我的 Issue', [['修复 Markdown 图片排版', '#128 · 打开', 'radio_button_checked']]],
  ['my-pulls', '我的 PR', [['优化客户端页面', '#130 · 等待审查', 'call_split']]],
  ['repositories', '仓库', [['Etoile / android', 'Kotlin · 128 ★', 'folder']]],
  ['stars', '星标收藏', [['android / compose-samples', 'Android · Kotlin', 'star']]],
  ['organizations', '组织', [['Etoile Community', '开源协作', 'group']]],
  ['followers', '关注者', [['android-maintainer', '@android-maintainer', 'person']]],
  ['following', '已关注', [['compose-developer', '@compose-developer', 'person']]],
  ['accounts', 'GitHub 账户', [['etoile', '当前账户', 'person']]],
  ['gitlab', 'GitLab 账户', [['etoile · gitlab.com', '当前账户', 'hub']]],
  ['sources', '应用来源', [['Etoile / android', '发布 Android 应用', 'source']]],
  ['app', 'AntennaPod', [['下载 APK', 'Android 7.0+ · 15.8 MB', 'download']]]
]) { const s = screen(workspace, id, title, '入口对应当前应用功能，详细设计见对应页面族。'); s.list(112, rows); }
const settingsQuick = screen(workspace, 'settings', '设置', '设置的三个二级入口与当前代码一致。'); settingsQuick.list(112, [['外观', 'Material 3 Expressive · 跟随系统', 'palette'], ['语言', '中文', 'language'], ['显示缩放', '100%', 'text_fields']]);

const repos = project('repositories', '仓库与阅读', '保持当前二级更多结构，优先展示关注和浏览；阅读区不与管理表单争空间。');
const repo = screen(repos, 'repository', 'android', '简介与关注操作优先。主层只保留代码、Issue、PR、发行版、更多。', { trailing: 'open_in_new' });
repo.text(104, 'Etoile / android'); repo.title(136, '你的开源工作空间'); repo.text(176, '在 Android 上浏览 GitHub 与 GitLab 项目');
repo.run('x', 16, 224, [['star', 'Star · 128'], ['visibility', 'Watch · 12'], ['fork_right', 'Fork · 34']].map(([icon, label]) => ({ kind: 'button', label, icon, size: 126, variant: 'tonal', ...(icon === 'fork_right' ? { action: go('fork-confirm') } : { toggle: { label: icon === 'star' ? '已加星 · 129' : '已关注 · 13', variant: 'filled' } }) })));
repo.button(304, '浏览代码', 'files', { icon: 'code' }); repo.list(384, [['Issue', '4 个打开', 'radio_button_checked', 'issues'], ['Pull Request', '', 'call_split', 'pulls'], ['发行版', '', 'new_releases', 'releases'], ['更多', 'Actions、分支、成员与管理', 'more_horiz', 'more']]); repo.title(708, 'README'); repo.text(752, '支持 GitHub 与 GitLab 的原生客户端');
const more = screen(repos, 'more', '更多', '按协作、版本、成员与管理分组；低频功能不再平铺在仓库主页。'); more.text(104, 'Etoile / android');
more.list(144, [['讨论', '', 'forum', 'discussions'], ['Actions', '', 'play_circle', 'actions'], ['提交', '', 'history', 'commits'], ['分支', 'main', 'account_tree', 'branches'], ['标签', '', 'sell', 'tags'], ['协作者', '', 'group', 'collaborators'], ['仓库信息与管理', '主题、分支保护、设置与 Webhooks', 'settings', 'administration']]);
const files = screen(repos, 'files', '代码', '分支选择紧凑展示；目录、文件各自保持一行主信息，长路径完整可读。'); files.button(104, 'main', 'branches', { variant: 'tonal', icon: 'account_tree', size: 180 }); files.text(176, '/'); files.list(216, [['app', '目录', 'folder', 'files'], ['docs', '目录', 'folder', 'files'], ['README.md', 'Markdown · 4.2 KB', 'description', 'reader'], ['build.gradle.kts', 'Kotlin · 2.8 KB', 'code', 'code-reader']]);
const reader = screen(repos, 'reader', 'README.md', '保留原生Markdown能力：可读正文、图片约束、代码横向滚动、链接可点击。'); reader.text(104, 'main / README.md'); reader.title(152, 'Etoile'); reader.text(200, '浏览、协作与管理你的开源项目。'); reader.title(264, '开始使用'); reader.text(312, '登录 GitHub 或 GitLab，浏览项目和处理讨论。'); reader.card(372, '代码示例', 'git clone https://github.com/Etoile/android', { size2: 96, fill: 'surfaceContainerHigh' }); reader.title(516, '项目特性'); reader.text(564, '账户管理 · Issue / PR · 构建与发行版');
const admin = screen(repos, 'administration', '仓库信息与管理', '当前权限决定可编辑项。危险设置仍需仓库名确认，不把确认按钮常驻于主页面。'); admin.list(112, [['默认分支', 'main', 'account_tree'], ['主题', 'android · kotlin', 'sell'], ['分支保护', '检查与审查要求', 'verified_user'], ['仓库管理', '描述、功能、默认分支', 'settings'], ['Webhooks', '配置与投递记录', 'link', 'webhooks'], ['归档仓库', '只读 · 需要二次确认', 'archive', 'archive-confirm']]);
for (const [id, title, rows] of [
  ['issues', 'Issue', [['修复 Markdown 图片排版', '#128 · 打开 · 6条回复', 'radio_button_checked']]], ['pulls', 'Pull Request', [['优化页面信息层级', '#130 · 等待审查', 'call_split']]],
  ['discussions', '讨论', [['下个版本的方向', '想法 · 12条回复', 'forum']]], ['actions', 'Actions', [['Android CI', 'android.yml · 已启用', 'play_circle']]],
  ['branches', '分支', [['main', '默认分支 · 受保护', 'account_tree', 'files'], ['feature/layout', '最近更新 · 可比较', 'account_tree', 'files']]],
  ['tags', '标签', [['v1.4.0', '最近发布', 'sell', 'releases']]], ['releases', '发行版', [['Etoile 1.4.0', '最新版本 · 3个附件', 'new_releases', 'release']]],
  ['commits', '提交', [['统一页面信息层级', 'a2f4d6c · etoile · 今天', 'history', 'diff']]], ['collaborators', '协作者', [['android-maintainer', '维护者 · 128次贡献', 'person'], ['compose-developer', '贡献者 · 32次贡献', 'person']]],
  ['webhooks', 'Webhooks', [['构建通知', '已启用 · 上次 200 OK', 'link', 'deliveries']]], ['deliveries', '投递记录', [['push · #108', '成功 · 200 OK', 'check_circle']]]
]) { const s = screen(repos, id, title, '标题、状态、元信息分层；搜索和分页沿用当前能力，管理入口在工具栏或行末。'); s.list(112, rows); }
const release = screen(repos, 'release', 'Etoile 1.4.0', '附件入口在长篇发布说明前，删除与编辑仍在管理菜单。'); release.filters(104, ['最新版本', 'v1.4.0']); release.button(168, '查看附件 · 3', 'assets', { icon: 'download', variant: 'tonal' }); release.title(264, '更新内容'); release.text(312, '改进代码阅读、账号切换与页面信息层级。'); release.card(368, '阅读发布说明', '正文图片、代码块、表格按阅读宽度显示。', { size2: 160 });
const assets = screen(repos, 'assets', '发行版附件', '完整文件名可换行，平台与大小次级展示。'); assets.list(112, [['Etoile-arm64-v8a.apk', 'Android · 28.4 MB', 'android'], ['Etoile-armeabi-v7a.apk', 'Android · 26.1 MB', 'android'], ['Source code', 'zip · v1.4.0', 'folder_zip']]);
for (const [id, title] of [['diff', '提交差异'], ['code-reader', 'build.gradle.kts']]) { const s = screen(repos, id, title, '文件名与路径分层，代码横向滚动，正文无多余卡片层级。'); s.text(104, 'app / build.gradle.kts'); s.card(160, '代码预览', 'plugins {\n    alias(libs.plugins.android.application)\n}\n\nandroid {\n    namespace = "takagi.ru.monica"\n}', { size2: 400, fill: 'surfaceContainerHigh' }); }
for (const [id, title, body] of [['fork-confirm', '复刻仓库？', '将 Etoile / android 复刻到你的账户。'], ['archive-confirm', '归档仓库？', '归档后仓库将变为只读。请输入 Etoile/android 确认。']]) {
  const s = screen(repos, id, title, '原生为确认对话框：说明影响与具体目标；取消不写入，提交中禁用，失败保留目标。');
  s.card(208, title, body, {size2: id === 'archive-confirm' ? 320 : 248, fill:'surfaceContainerHigh'});
  if (id === 'archive-confirm') s.field(336,'输入 Etoile/android');
  const py = id === 'archive-confirm' ? 432 : 360;
  s.button(py,'取消','back',{x:32,size:164,variant:'text'}); s.button(py,id === 'archive-confirm' ? '归档' : '复刻',null,{x:212,size:164});
}

const collaboration = project('collaboration', '讨论与审查', '沿用当前Issue、PR、审查与讨论能力。标题状态先于正文，管理表单按需打开。');
const issue = screen(collaboration, 'issue', 'Issue #128', '保留评论入口；标签、成员、里程碑及状态管理进入更多面板。', { trailing: 'more_vert', target: 'management' }); issue.filters(104, ['打开', 'bug']); issue.title(160, '修复 Markdown 图片排版'); issue.text(208, 'etoile · 今天 · 6条回复'); issue.card(264, '问题说明', 'README 中的宽图和表格应适配屏幕，代码块允许独立横向滚动。', { size2: 180 }); issue.list(484, [['android-maintainer', '可以补充一下出现问题的文件吗？', 'person']]); issue.button(680, '添加评论', 'comment', { icon: 'add_comment', variant: 'tonal' });
const pull = screen(collaboration, 'pull', 'PR #130', '保留概览、活动、文件三页签；审查优先，低频管理进入更多。', { trailing: 'more_vert', target: 'management' }); pull.filters(104, ['打开', '等待审查']); pull.title(160, '统一客户端页面信息层级'); pull.text(208, 'feature/layout → main'); pull.tabs(256, ['概览', '活动', '文件']); pull.card(328, '变更说明', '优化导航和内容密度，保留现有操作和危险确认。', { size2: 164 }); pull.list(524, [['3 个文件', '+128 −74', 'difference', 'review']]); pull.button(652, '审查更改', 'review', { icon: 'rate_review' });
const management = screen(collaboration, 'management', '更多操作', '原生为底部面板，只显示当前用户有权限执行的操作。'); management.list(112, [['编辑内容', '', 'edit', 'edit'], ['标签与负责人', '', 'label', 'metadata'], ['审查者', '', 'group', 'metadata'], ['合并 Pull Request', '', 'merge', 'merge'], ['关闭或重新打开', '需要确认', 'check_circle'], ['锁定讨论', '需要确认', 'lock']]);
for (const [id, title, rows] of [['issues', 'Issue', [['修复 Markdown 图片排版', '#128 · 打开 · 6条回复', 'radio_button_checked', 'issue']]], ['pulls', 'Pull Request', [['统一客户端页面信息层级', '#130 · 等待审查', 'call_split', 'pull']]], ['discussions', '讨论', [['下个版本的方向', '想法 · 12条回复', 'forum', 'discussion']]]]) { const s = screen(collaboration, id, title, '状态筛选、列表、分页同一滚动区域，创建入口只出现一次。'); s.search(104, '搜索'); s.filters(176, ['打开', '已关闭', '全部']); s.list(244, rows); s.part('extendedFab', 248, 700, '新建', { icon: 'add', action: go('edit') }); }
const discussion = screen(collaboration, 'discussion', '下个版本的方向', '主题、正文、回复与回复线程逐层阅读。'); discussion.text(104, '想法 · etoile · 12条回复'); discussion.card(160, '下一步体验优化', '讨论GitHub与GitLab的协作体验。', { size2: 180 }); discussion.list(396, [['android-maintainer', '先统一列表、阅读和管理的操作顺序。', 'person']]); discussion.button(652, '回复', 'comment', { variant: 'tonal' });
for (const [id, title] of [['edit', '编辑内容'], ['comment', '添加评论']]) { const s = screen(collaboration, id, title, '使用已有独立编辑器，键盘出现时可滚动；草稿与失败输入保留。', { trailing: 'check' }); if(id === 'edit') s.field(112, '标题'); s.field(id === 'edit' ? 200 : 112, '支持 Markdown', { supporting: '编辑 / 预览' }); s.card(288, '编辑区', '内容随输入增长，提交按钮在工具栏；退出未保存内容会确认。', { size2: 300 }); }
const review = screen(collaboration, 'review', '审查更改', '代码差异与审查工作区沿用当前实现。审查意见不夹在长列表底部。'); review.text(104, 'app / GithubNavChrome.kt'); review.card(160, '代码差异', '+ 统一页面间距与操作位置\n− 移除重复的功能入口', { size2: 220, fill: 'surfaceContainerHigh' }); review.field(416, '审查意见'); review.filters(496, ['评论', '批准', '请求更改']); review.button(584, '提交审查', null, { icon: 'rate_review' });
const merge = screen(collaboration, 'merge', '合并 Pull Request', '与当前合并表单一致，选择方式后再次确认；提交使用所审查的head SHA。'); merge.text(104, 'feature/layout → main'); merge.filters(160, ['Merge', 'Squash', 'Rebase']); merge.field(232, '提交标题'); merge.field(320, '提交说明'); merge.text(412, '确认前检查目标分支与当前提交'); merge.button(488, '继续确认', null, { icon: 'merge' });
const metadata = screen(collaboration, 'metadata', '标签与负责人', '多选列表和保存分层，加载失败可重试，保存失败不丢选择。'); metadata.search(104, '搜索标签或用户'); metadata.list(184, [['bug', '已选择', 'check_circle'], ['enhancement', '', 'label'], ['android-maintainer', '负责人', 'person']]); metadata.button(652, '保存', 'back');

const accounts = project('accounts', '账户与设置', '当前多账户和GitLab能力完整保留；登录方法、偏好与安全确认各自有明确层级。');
const account = screen(accounts, 'accounts', '账户', '服务与实例明确标注，头像左、身份右；账号切换与移除不混淆。'); account.list(112, [['etoile', 'GitHub · 当前账户', 'person'], ['etoile-reviewer', 'GitHub · 点击切换', 'person'], ['etoile', 'gitlab.com', 'hub', 'gitlab-accounts']]); account.button(388, '添加 GitHub 账户', 'signin', { icon: 'add' }); account.button(468, '管理 GitLab 账户', 'gitlab-accounts', { variant: 'tonal', icon: 'hub' });
const signin = screen(accounts, 'signin', '登录 GitHub', '浏览公开项目无需登录。浏览器OAuth为主入口，设备代码和令牌留作明确替代方法。'); signin.card(112, '连接 GitHub', '同步仓库、通知与收藏', { icon: 'account_circle', fill: 'primaryContainer', size2: 148 }); signin.button(300, '在浏览器中登录', null, { icon: 'login' }); signin.list(396, [['使用设备代码', '在其他设备完成授权', 'devices', 'device'], ['使用个人访问令牌', '在本机安全保存', 'key', 'token']]);
const device = screen(accounts, 'device', '设备代码', '显示已有设备授权流程与轮询状态，取消停止等待。'); device.card(136, 'ABCD-1234', '在 github.com/login/device 输入代码', { fill: 'primaryContainer', size2: 156 }); device.button(336, '打开授权网页', null, { icon: 'open_in_new' }); device.text(440, '等待浏览器完成授权…'); device.button(508, '取消', 'back', { variant: 'text' });
const token = screen(accounts, 'token', '个人访问令牌', '输入隐藏显示，不在设计稿放真实凭据。'); token.field(128, '访问令牌', { icon: 'key', supporting: '凭据仅在本机安全保存' }); token.button(264, '登录', null);
const glAccounts = screen(accounts, 'gitlab-accounts', 'GitLab 账户', '实例和身份明确；浏览器与PAT分别显示各自表单，授权中禁切换。'); glAccounts.list(112, [['etoile', 'gitlab.com · 当前账户', 'person']]); glAccounts.title(228, '添加账户'); glAccounts.field(280, '实例地址', { supporting: 'https://gitlab.com 或自建实例' }); glAccounts.filters(368, ['浏览器 OAuth', '访问令牌']); glAccounts.field(432, '应用 ID', { supporting: '使用已配置的GitLab OAuth应用' }); glAccounts.button(548, '在浏览器中登录', null, { icon: 'login' });
const settings = screen(accounts, 'settings', '设置', '保留三个独立二级页面，不把所有选择展开在首页。'); settings.list(112, [['外观', 'Material 3 Expressive · 跟随系统', 'palette', 'appearance'], ['语言', '中文', 'language', 'language'], ['显示缩放', '100%', 'text_fields', 'scale']]);
const appearance = screen(accounts, 'appearance', '外观', '保留三种风格与配色选项；M3E采用一致的语义色和字号层级。'); appearance.card(104, 'Material 3 Expressive', '内容清晰，操作有层次', { icon: 'palette', fill: 'primaryContainer', size2: 112 }); appearance.title(248, '设计风格'); appearance.filters(296, ['Nothing', 'M3E', 'Miuix'], 1); appearance.title(368, '明暗模式'); appearance.filters(416, ['跟随系统', '浅色', '深色']); appearance.title(488, '配色'); appearance.filters(536, ['默认', '海洋蓝', '科技紫']); appearance.filters(584, ['日落橙', '森林绿', 'MIUI 蓝']);
const language = screen(accounts, 'language', '语言', '语言用本名展示，修改立即生效并保留返回位置。'); language.list(112, [['跟随系统', '', 'radio_button_unchecked'], ['English', '', 'radio_button_unchecked'], ['中文', '当前选择', 'radio_button_checked'], ['Tiếng Việt', '', 'radio_button_unchecked'], ['日本語', '', 'radio_button_unchecked'], ['Русский', '', 'radio_button_unchecked']]);
const scale = screen(accounts, 'scale', '显示缩放', '百分比预览与滑块保留；布局由可读宽度决定，字号增加后恢复单列。'); scale.card(112, '100%', '调整界面显示大小', { fill: 'primaryContainer', size2: 132 }); scale.part('slider', 16, 296, '', { value: 50 }); scale.text(376, '较小                                      较大');

const people = project('people', '用户与收藏', '身份、横向统计、头像横行、仓库列表形成统一的个人与项目关系视图。');
const pub = screen(people, 'public-profile', '开发者', '头像和身份在同一区域，统计横排，关注操作在简介旁。'); pub.card(112, 'android-maintainer', '@android-maintainer · Compose 与开源工具', { icon: 'person', fill: 'primaryContainer', size2: 140 }); pub.run('x', 16, 272, ['42 仓库', '128 关注者', '32 已关注'].map((label, i) => ({ kind: 'button', label, size: 126, variant: 'tonal', action: go(['repositories', 'followers', 'following'][i]) }))); pub.button(352, '关注', null, { variant: 'tonal', icon: 'person_add' }); pub.title(448, '公开仓库'); pub.list(496, [['Etoile / android', 'Kotlin · 128 ★', 'folder', 'repositories']]);
for (const [id, title] of [['followers', '关注者'], ['following', '已关注'], ['blocked', '已屏蔽用户'], ['organizations', '组织']]) { const s = screen(people, id, title, '头像固定靠左，名称和身份信息从头像右侧紧贴排列并左对齐；宽屏可适配两列，不再用头像磁贴。'); identities(s,112,[['android-maintainer', '@android-maintainer', 'person', 'public-profile'], ['compose-developer', '@compose-developer', 'person', 'public-profile'], ['Etoile Community', '开源工具与协作', 'group', 'public-profile']]); }
const starred = screen(people, 'stars', '星标收藏', '沿用当前全量分页、分类与批量标记；列表优先，标签管理独立。', { trailing: 'label', target: 'labels' }); starred.search(104, '搜索星标仓库'); starred.filters(176, ['全部', '未分类', 'Android', '设计']); starred.list(244, [['android / compose-samples', 'Android · Kotlin · 24.8k ★', 'star', 'repositories'], ['lnkiai / m3e-canvas', '设计 · TypeScript', 'star', 'repositories']]);
const labels = screen(people, 'labels', '管理标签', '标签管理不与仓库阅读混排，删除保持确认，长列表与创建表单均可到达。'); labels.list(112, [['Android', '24个仓库', 'label'], ['设计', '12个仓库', 'label'], ['稍后阅读', '8个仓库', 'label']]); labels.field(388, '新标签名称'); labels.button(484, '创建标签', null, { icon: 'add' });
const repositories = screen(people, 'repositories', '仓库', '共享仓库列表行，名称、描述、语言、星标顺序固定。'); repositories.search(104, '搜索仓库'); repositories.list(184, [['Etoile / android', 'GitHub与GitLab客户端 · Kotlin · 128 ★', 'folder'], ['Etoile / design', '设计资料 · 32 ★', 'folder']]);

const automation = project('automation', '构建与管理', '保持Actions、工作流操作、日志、Webhook投递和复投功能，管理操作逐步展开。');
const workflows = screen(automation, 'workflows', 'Actions', '工作流标题与路径先显示，启停与触发收进行菜单，危险操作确认。'); workflows.list(112, [['Android CI', '.github/workflows/android.yml · 已启用', 'play_circle', 'runs'], ['Release', '.github/workflows/release.yml · 已启用', 'play_circle', 'runs']]);
const runs = screen(automation, 'runs', 'Android CI', '运行状态、分支、触发者和时间固定层级。'); runs.list(112, [['统一客户端页面信息层级', '#108 · 成功 · main · 3分钟前', 'check_circle', 'run'], ['修复账号切换', '#107 · 失败 · feature/auth', 'error', 'run']]);
const run = screen(automation, 'run', 'Build #108', '运行概要、作业、附件分组；取消、重跑在更多中确认。', { trailing: 'more_vert', target: 'run-actions' }); run.card(112, '构建成功', 'main · etoile · 3分钟', { icon: 'check_circle', fill: 'secondaryContainer', size2: 116 }); run.title(264, '作业'); run.list(312, [['Build Android preview', '成功 · 2分48秒', 'check_circle', 'job']]); run.title(444, '构建产物'); run.list(492, [['Android debug packages', '64.2 MB · 保留30天', 'inventory_2', 'artifacts']]);
const job = screen(automation, 'job', '作业日志', '元信息收在摘要，步骤可展开，日志独立换行或横向滚动。'); job.text(104, 'Build Android preview · 成功'); job.filters(152, ['自动换行', '横向滚动']); job.list(216, [['Checkout', '成功 · 4秒', 'check_circle'], ['Build', '成功 · 2分12秒', 'check_circle']]); job.card(396, '构建日志', '> Task :app:compileDebugKotlin\n> Task :app:testDebugUnitTest\n> Task :app:lintDebug\n\nBUILD SUCCESSFUL', { size2: 320, fill: 'surfaceContainerHigh' });
const runActions = screen(automation, 'run-actions', '运行操作', '只显示有权限操作，重跑/取消确认具体运行。'); runActions.list(112, [['重新运行全部作业', '需要确认 · Build #108', 'refresh'], ['重新运行失败作业', '需要确认', 'replay'], ['取消运行', '仅运行中可用 · 需要确认', 'cancel']]);
for (const [id, title, rows] of [['artifacts', '构建产物', [['Android debug packages', '64.2 MB · 尚未过期', 'download']]], ['webhooks', 'Webhooks', [['构建通知', 'https://example.test/build · 已启用', 'link', 'deliveries']]], ['deliveries', '投递记录', [['push · #108', '成功 · 200 OK · 1.2秒', 'check_circle'], ['pull_request · #107', '失败 · 503 · 可重新投递', 'error']]]]) { const s = screen(automation, id, title, '主信息与状态优先，低频写操作在行菜单，失败保留原状态并可重试。'); s.list(112, rows); }

const gitlab = project('gitlab', 'GitLab 工作流', '沿用当前项目、代码、Issue、MR、Pipeline和日志能力，与GitHub使用同一阅读和列表规则。');
const glProjects = screen(gitlab, 'projects', 'GitLab 项目', '实例在标题下可见，搜索与项目列表共滚；不把项目当孤立大卡片。'); glProjects.text(104, 'gitlab.com · etoile'); glProjects.search(152, '搜索项目'); glProjects.list(240, [['Etoile / android', '原生客户端 · 128 ★ · 34 forks', 'folder', 'project'], ['Etoile / website', '项目网站 · 32 ★', 'folder', 'project']]);
const glProject = screen(gitlab, 'project', 'Etoile / android', '根目录展示简介和三种协作入口，进入子目录后直接浏览内容。'); glProject.text(104, 'gitlab.com · 公开项目'); glProject.title(144, 'Android 工作空间'); glProject.run('x', 16, 208, [['Issue', 'issues'], ['MR', 'mrs'], ['Pipeline', 'pipelines']].map(([label, to]) => ({ kind: 'button', label, size: 126, variant: 'tonal', action: go(to) }))); glProject.button(288, 'main', 'revision', { size: 182, variant: 'tonal', icon: 'account_tree' }); glProject.list(376, [['app', '目录', 'folder', 'tree'], ['docs', '目录', 'folder', 'tree'], ['README.md', '文件', 'description', 'file']]);
const tree = screen(gitlab, 'tree', 'app', '目录层级不再重复项目简介与所有协作功能。'); tree.button(104, 'main', 'revision', { size: 182, variant: 'tonal', icon: 'account_tree' }); tree.text(184, 'Etoile / android / app'); tree.list(232, [['上级目录', '', 'arrow_upward', 'project'], ['src', '目录', 'folder', 'tree'], ['build.gradle.kts', '文件', 'code', 'file']]);
const glFile = screen(gitlab, 'file', 'build.gradle.kts', '保留行号、选择复制和大文件截断提示，不重复项目概览。'); glFile.text(104, 'main / app / build.gradle.kts'); glFile.card(168, '代码预览', '1  plugins {\n2      alias(libs.plugins.android.application)\n3  }\n4\n5  android {\n6      namespace = "takagi.ru.monica"\n7  }', { size2: 440, fill: 'surfaceContainerHigh' });
const revision = screen(gitlab, 'revision', '选择修订', '原生为对话框，取消不触发加载；应用后回到根目录。'); revision.field(160, '分支、标签或提交', { supporting: 'main' }); revision.text(264, '当前提交  a2f4d6c8e0'); revision.button(352, '应用修订', 'project');
for (const [id, title, rows] of [['issues', 'Issue', [['修复窄屏导航', '#128 · 打开 · etoile', 'radio_button_checked', 'conversation']]], ['mrs', 'Merge Request', [['完善页面设计', '!130 · 打开 · feature/layout → main', 'call_split', 'conversation']]], ['pipelines', 'Pipeline', [['Pipeline #108', '成功 · main · a2f4d6c', 'check_circle', 'pipeline']]]]) { const s = screen(gitlab, id, title, '跨服务采用相同的标题、状态和元信息顺序，管理沿用现有权限与确认。'); s.filters(104, ['打开', '已关闭', '全部']); s.search(168, id === 'pipelines' ? '按分支筛选' : '搜索'); s.list(256, rows); }
const conversation = screen(gitlab, 'conversation', '讨论详情', '标题、状态和作者先于正文；关闭、合并、评论等沿用现有权限与确认。'); conversation.filters(104, ['打开']); conversation.title(160, '完善页面设计'); conversation.text(208, 'etoile · gitlab.com'); conversation.card(264, '说明', '统一项目浏览、讨论、运行与账户的操作方式。', { size2: 188 }); conversation.button(612, '添加评论', null, { variant: 'tonal' });
const pipeline = screen(gitlab, 'pipeline', 'Pipeline #108', '运行概要和作业优先，取消、重试、手动作业按权限确认。'); pipeline.card(112, '运行成功', 'main · a2f4d6c · 3分钟', { icon: 'check_circle', fill: 'secondaryContainer', size2: 116 }); pipeline.title(264, '作业'); pipeline.list(312, [['build', '成功 · 2分12秒', 'check_circle', 'log'], ['test', '成功 · 48秒', 'check_circle', 'log']]);
const log = screen(gitlab, 'log', '作业日志', '日志沿用安全脱敏与截断规则，换行与复制不混入写操作。'); log.card(112, 'build · 日志', '$ ./gradlew testDebugUnitTest\n\nBUILD SUCCESSFUL\n\nJob succeeded', { size2: 540, fill: 'surfaceContainerHigh' });

const states = project('states', '状态与宽屏规则', '覆盖加载、空列表、失败、提交、权限和危险确认。状态表达沿用实际行为，不伪造成功。');
const loading = screen(states, 'loading', '仓库', '首次加载使用列表骨架，刷新保留已有内容；不以巨大留白表示等待。'); loading.part('loadingIndicator', 182, 256); loading.text(336, '正在加载仓库…');
const empty = screen(states, 'empty', '星标收藏', '清空筛选与去发现项目分别有意义，空状态说明下一步。'); empty.search(104, '搜索星标仓库'); empty.card(248, '暂无匹配项目', '试试其他关键词，或清空筛选。', { icon: 'search_off', size2: 152 }); empty.button(448, '清空筛选', null, { variant: 'tonal' });
const failure = screen(states, 'error', '仓库', '错误在失败内容附近显示，保留已有结果并提供重试；不整页清空。'); failure.list(112, [['Etoile / android', '上次加载的内容', 'folder']]); failure.card(264, '暂时无法加载更多', '检查网络后重试，已显示的项目会保留。', { icon: 'wifi_off', size2: 132 }); failure.button(440, '重试', null, { variant: 'tonal' });
const busy = screen(states, 'busy', '保存内容', '提交中禁止重复写入，失败保留草稿。'); busy.field(112, '标题', { supporting: '优化客户端页面' }); busy.card(216, '草稿内容', '你的输入在提交失败后仍会保留。', { size2: 200 }); busy.part('linearProgress', 16, 472, '', { wavy: true }); busy.text(532, '正在保存…');
const denied = screen(states, 'permission', '仓库管理', '区分未登录和权限不足，给出可执行入口，不诱导无效写入。'); denied.card(184, '需要管理权限', '使用拥有此仓库管理权限的账户后再试。', { icon: 'lock', size2: 152 }); denied.button(384, '管理账户', null, { variant: 'tonal' });
const danger = screen(states, 'danger', '删除确认', '原生为确认对话框：具体目标和影响清楚，取消是完整退出路径。'); danger.card(240,'删除 Webhook？','目标：https://example.test/build。删除后将停止发送事件，此操作无法撤销。',{size2:248,fill:'surfaceContainerHigh'}); danger.button(400,'取消','back',{x:32,size:164,variant:'text'}); danger.button(400,'删除',null,{x:212,size:164});

const manifest = [];
for (const { key, doc } of projects) {
  const json = JSON.stringify(doc, null, key === 'workspace' ? 0 : 2) + '\n';
  writeFileSync(join(out, `${key}.json`), json);
  manifest.push({ key, title: doc.title, frames: doc.frames.length, bytes: Buffer.byteLength(json), link: 'https://lnkiai.github.io/m3e-canvas/#docz=' + deflateRawSync(Buffer.from(json)).toString('base64url') });
}
writeFileSync(join(out, 'links.json'), JSON.stringify(manifest, null, 2));
const cards = manifest.map(p => `<article><span>${p.frames} 个画面 · 深浅色</span><h2>${p.title.replace('Etoile · ', '')}</h2><p>${projects.find(x => x.key === p.key).doc.brief.split(' 基于')[0]}</p><a href="${p.link}" target="_blank" rel="noopener">在 M3E Canvas 编辑 ↗</a><a class="secondary" href="${p.key}.json" download>下载 JSON</a></article>`).join('');
writeFileSync(join(out, 'index.html'), `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Etoile · 当前页面 M3E 优化</title><style>*{box-sizing:border-box}body{margin:0;background:#141218;color:#e6e0e9;font:16px/1.65 system-ui,sans-serif}main{max-width:1200px;margin:auto;padding:64px 24px}header{max-width:760px;margin-bottom:48px}small,span{color:#cac4d0}h1{font-size:clamp(32px,5vw,56px);line-height:1.15;letter-spacing:-1px;margin:16px 0 24px}h2{font-size:23px;line-height:1.3;margin:12px 0}p{color:#cac4d0}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:16px}article{background:#211f26;border-radius:28px;padding:28px;display:flex;flex-direction:column}article p{flex:1}a{color:#eaddff;background:#4f378b;border-radius:99px;padding:12px 20px;text-decoration:none;display:block;text-align:center;min-height:48px;margin-top:12px}.secondary{background:transparent;color:#d0bcff}a:focus-visible{outline:3px solid #d0bcff;outline-offset:4px}footer{margin-top:48px;color:#cac4d0}img{max-width:100%;border-radius:24px}.previews{margin:48px 0}</style><main><header><small>ETOILE · 当前功能 · 2026-10-21</small><h1>内容先行，<br>操作各有位置。</h1><p>以当前 Android 客户端为基础优化页面，覆盖 GitHub 与 GitLab。保留功能、登录方式和危险操作确认，统一信息层级、组件、阅读和状态反馈。</p><p>这些是可编辑的 M3E Canvas 设计。设计预览与原生应用的验证分别记录，示例数据不是实时账户内容。</p></header><section class="grid">${cards}</section><section class="previews"><h2>Canvas 页面预览</h2><p>由实际 M3E Canvas 预览渲染；不代表 Android 设备截图。</p><img src="previews/contact-sheet.jpg" alt="当前Etoile页面的M3E Canvas设计预览" loading="lazy"></section><footer>字体：标题使用 Google Sans Flex / Roboto Flex，中文跟随系统；正文使用系统无衬线，代码与修订使用等宽字体。<br>基于 <a class="secondary" href="https://github.com/lnkiai/m3e-canvas">lnkiai/m3e-canvas</a> 的公开文档格式。旧画布未被作为本轮设计依据。</footer></main></html>`);
console.log(manifest.map(p => `${p.key}: ${p.frames} screens, ${p.bytes} bytes`).join('\n'));
