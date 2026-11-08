package com.pricelens.collect.adapters

import com.pricelens.collect.AdapterState
import com.pricelens.collect.UnavailableAdapter
import com.pricelens.domain.model.Platform

/**
 * 其余平台：如实标记不可用，不返回任何伪造数据。
 * 每个类的 stateMessage 都写明「实测到的阻塞原因」，接新平台时直接替换成真实实现即可。
 */
class TaobaoAdapter : UnavailableAdapter(
    platform = Platform.TAOBAO,
    state = AdapterState.NEEDS_LOGIN,
    stateMessage = "淘宝/天猫商品页需要登录 Cookie 并通过滑块验证，无公开接口；当前适配器未可用",
    riskNotes = "淘宝类目/搜索接口均需登录态与签名，无 Cookie 请求返回登录页；" +
        "强行模拟登录违反平台协议且有账号风险。"
)

class TmallAdapter : UnavailableAdapter(
    platform = Platform.TMALL,
    state = AdapterState.NEEDS_LOGIN,
    stateMessage = "天猫与淘宝同源，需要登录 Cookie；当前适配器未可用",
    riskNotes = "天猫商品详情与价格均依赖登录态；未登录页面只返回登录/验证页。"
)

class PddAdapter : UnavailableAdapter(
    platform = Platform.PDD,
    state = AdapterState.NEEDS_LOGIN,
    stateMessage = "拼多多商品数据主要在 App 内，H5 接口需要 anti_content 签名；当前适配器未可用",
    riskNotes = "拼多多对请求签名与设备指纹校验严格，网页端几乎不开放商品详情；" +
        "接入需要逆向签名，风险和协议问题由使用者自行承担。"
)

class DouyinAdapter : UnavailableAdapter(
    platform = Platform.DOUYIN,
    state = AdapterState.NEEDS_LOGIN,
    stateMessage = "抖音商城需要登录态与签名参数，分享页仅展示部分信息；当前适配器未可用",
    riskNotes = "抖音商品接口需要 msToken / a_bogus 等签名，且与账号绑定；无登录无法取价。"
)

class WeidianAdapter : UnavailableAdapter(
    platform = Platform.WEIDIAN,
    state = AdapterState.NOT_IMPLEMENTED,
    stateMessage = "微店商品页需登录且存在反爬；当前适配器未可用",
    riskNotes = "微店商品详情多由商家自行配置，价格与库存字段不稳定；后续可尝试登录态解析。"
)

class Ali1688Adapter : UnavailableAdapter(
    platform = Platform.ALI1688,
    state = AdapterState.NEEDS_LOGIN,
    stateMessage = "1688 详情页实测仅返回约 2.4KB 空壳，需登录；当前适配器未可用",
    riskNotes = "1688 对未登录请求返回空壳/登录页，价格需登录后由接口下发；" +
        "另有官方开放平台（需企业资质与 AppKey）。"
)
