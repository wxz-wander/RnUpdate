# RnUpdate

    这是一个Rn 模块下载更新,存储目录都已经设计完成,需要使用的时候,手动修改检查更新的url代码,之后就可以完成rn的热更新
    根据制定moduleId和version来下载更新的
    支持本地assets和网络两部分
    更新分为三步，
    第一步：判断是否需要复制assets目录下的bundle zip资源，需要就备份并复制
    第二步：检查版本是否需要更新，需要更新就弹窗，让用户选择下载更新
    第三步：下载并备份，解压zip ，并存放到指定目录。
    始终保证cache里面的bundle是最新的

# 存储目录设计

bundle目录

`cache/rn/<moduleId>/bundle/index.wallbill.bundle`
`cache/rn/<moduleId>/bundle/drawable-mdpi/`
`cache/rn/<moduleId>/bundle/raw/`

version文件

`cache/rn/<moduleId>/version.txt`