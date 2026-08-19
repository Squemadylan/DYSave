const fs = require('fs');
const path = 'E:/New/DYSave/app/src/main/java/com/douyin/downloader/ui/profile/ProfileScreen.kt';
let s = fs.readFileSync(path, 'utf8');
const nl = s.includes('\r\n') ? '\r\n' : '\n';
const startToken = `@Composable${nl}private fun XhsCookieEditor(`;
const endToken = `@Composable${nl}private fun SubdirEditor`;
const i = s.indexOf(startToken);
const j = s.indexOf(endToken, i);
if (i < 0 || j < 0) throw new Error(`anchors not found i=${i} j=${j}`);
const replacement = [
  '@Composable',
  'private fun XhsCookieEditor(',
  '    savedCookie: String,',
  '    onSave: (String) -> Unit,',
  '    onClear: () -> Unit,',
  '    onLogin: () -> Unit,',
  ') {',
  '    var text by remember { mutableStateOf("") }',
  '    YuanButton(',
  '        text = "登录获取",',
  '        onClick = onLogin,',
  '        modifier = Modifier.fillMaxWidth(),',
  '        style = YuanButtonStyle.Tonal,',
  '    )',
  '    Spacer(Modifier.height(8.dp))',
  '    OutlinedTextField(',
  '        value = text,',
  '        onValueChange = { text = it },',
  '        modifier = Modifier.fillMaxWidth(),',
  '        minLines = 3,',
  '        maxLines = 5,',
  '        shape = RoundedCornerShape(12.dp),',
  '        placeholder = { Text("或粘贴 Cookie 原文") },',
  '    )',
  '    if (savedCookie.isNotBlank()) {',
  '        Spacer(Modifier.height(8.dp))',
  '        Text(',
  '            text = "已配置",',
  '            style = MaterialTheme.typography.bodySmall,',
  '            color = MaterialTheme.colorScheme.primary,',
  '        )',
  '    }',
  '    Spacer(Modifier.height(8.dp))',
  '    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {',
  '        TextButton(',
  '            onClick = { onSave(text) },',
  '            enabled = text.isNotBlank(),',
  '        ) {',
  '            Text("保存")',
  '        }',
  '        TextButton(',
  '            onClick = {',
  '                text = ""',
  '                onClear()',
  '            },',
  '            enabled = savedCookie.isNotBlank() || text.isNotBlank(),',
  '        ) {',
  '            Text("清空")',
  '        }',
  '    }',
  '}',
  '',
  '',
].join(nl);
s = s.slice(0, i) + replacement + s.slice(j);
fs.writeFileSync(path, s, 'utf8');
console.log('ok');
