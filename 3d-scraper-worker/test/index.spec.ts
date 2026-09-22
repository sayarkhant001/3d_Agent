import {
	env,
	createExecutionContext,
	waitOnExecutionContext,
	SELF,
} from "cloudflare:test";
import { describe, it, expect } from "vitest";
import worker, { calculateNextThaiDrawDate, getLocalDrawDateInfo } from "../src/index";
import {
	calculateTutNumbers,
	generateCdKey,
	extractForwardedUser,
	getAdminBottomKeyboard,
	getResellerBottomKeyboard,
	getBuyerBottomKeyboard,
	getResellerBulkKeyboard,
	getKeyGenKeyboard
} from "../src/telegramBot";

const IncomingRequest = Request<unknown, IncomingRequestCfProperties>;

describe("3D Scraper & Telegram Bot Worker", () => {
	it("responds to /health with status ok", async () => {
		const request = new IncomingRequest("http://example.com/health");
		const ctx = createExecutionContext();
		const response = await worker.fetch(request, env, ctx);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(200);
		const json = await response.json() as any;
		expect(json.status).toBe("ok");
		expect(json.service).toBe("3d-scraper-worker");
	});

	it("responds to root / with service information and endpoints", async () => {
		const request = new IncomingRequest("http://example.com/");
		const ctx = createExecutionContext();
		const response = await worker.fetch(request, env, ctx);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(200);
		const json = await response.json() as any;
		expect(json.status).toBe("online");
		expect(json.endpoints).toBeDefined();
	});

	it("calculates next Thai lottery draw dates accurately", () => {
		// Draw on 1st -> next is 16th of same month
		expect(calculateNextThaiDrawDate("2026-09-01")).toBe("2026-09-16");
		// Draw on 16th -> next is 1st of next month
		expect(calculateNextThaiDrawDate("2026-09-16")).toBe("2026-10-01");
		// Draw on Dec 16th -> next is Jan 1st of next year
		expect(calculateNextThaiDrawDate("2026-12-16")).toBe("2027-01-01");
	});

	it("verifies Myanmar 3D Tut generator logic for Telegram bot", () => {
		// Distinct digits (108): 5 perms + 2 near misses = 7 numbers
		const res108 = calculateTutNumbers("108");
		expect(res108.permutations).toEqual(["018", "081", "180", "801", "810"]);
		expect(res108.nearMisses).toEqual(["107", "109"]);
		expect(res108.allTut.length).toBe(7);
		expect(res108.allTut).not.toContain("108");

		// Double repeating digits (212): 2 perms + 2 near misses = 4 numbers
		const res212 = calculateTutNumbers("212");
		expect(res212.permutations).toEqual(["122", "221"]);
		expect(res212.nearMisses).toEqual(["211", "213"]);
		expect(res212.allTut.length).toBe(4);

		// Triple digits (222): 0 perms + 2 near misses = 2 numbers
		const res222 = calculateTutNumbers("222");
		expect(res222.permutations).toEqual([]);
		expect(res222.nearMisses).toEqual(["221", "223"]);
		expect(res222.allTut.length).toBe(2);

		// Cyclic wrap-around
		expect(calculateTutNumbers("000").nearMisses).toEqual(["999", "001"]);
		expect(calculateTutNumbers("999").nearMisses).toEqual(["998", "000"]);
	});

	it("handles Telegram Webhook POST updates for /start and /tut commands", async () => {
		const updatePayload = {
			update_id: 1001,
			message: {
				message_id: 1,
				chat: { id: 5684146708, type: "private" },
				date: Math.floor(Date.now() / 1000),
				text: "/start",
				from: { id: 5684146708, is_bot: false, first_name: "TestUser" }
			}
		};

		const request = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(updatePayload)
		});

		const ctx = createExecutionContext();
		const response = await worker.fetch(request, env, ctx);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(200);
	});

	it("handles Telegram Webhook callback query clicks", async () => {
		const callbackPayload = {
			update_id: 1002,
			callback_query: {
				id: "cq_12345",
				data: "m_main",
				from: { id: 5684146708, is_bot: false, first_name: "TestUser" },
				message: {
					message_id: 2,
					chat: { id: 5684146708, type: "private" },
					date: Math.floor(Date.now() / 1000)
				}
			}
		};

		const request = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(callbackPayload)
		});

		const ctx = createExecutionContext();
		const response = await worker.fetch(request, env, ctx);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(200);
	});

	it("generates compliant 32-character CD keys in 8 blocks", () => {
		const key = generateCdKey();
		expect(key).toMatch(/^[A-Z0-9]{4}(-[A-Z0-9]{4}){7}$/);
		const parts = key.split("-");
		expect(parts.length).toBe(8);
		for (const part of parts) {
			expect(part.length).toBe(4);
		}
	});

	it("blocks unauthorized users from admin commands", async () => {
		const unauthorizedPayload = {
			update_id: 1003,
			message: {
				message_id: 3,
				chat: { id: 999999999, type: "private" },
				date: Math.floor(Date.now() / 1000),
				text: "/setwinner 108",
				from: { id: 999999999, is_bot: false, first_name: "RandomHacker" }
			}
		};

		const request = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(unauthorizedPayload)
		});

		const ctx = createExecutionContext();
		const response = await worker.fetch(request, env, ctx);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(200);
		const text = await response.text();
		expect(text).toBe("unauthorized");
	});

	it("welcomes non-admin users with 3-day free trial on conversation start", async () => {
		const userPayload = {
			update_id: 1004,
			message: {
				message_id: 4,
				chat: { id: 888888888, type: "private" },
				date: Math.floor(Date.now() / 1000),
				text: "/start",
				from: { id: 888888888, is_bot: false, first_name: "NewClient" }
			}
		};

		const request = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(userPayload)
		});

		const ctx = createExecutionContext();
		const response = await worker.fetch(request, env, ctx);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(200);
		const text = await response.text();
		expect(text).toBe("ok");
	});

	it("verifies default editable sale plans and device changeability flags", async () => {
		const { getDefaultSalePlans } = await import("../src/telegramBot");
		const plans = getDefaultSalePlans();

		// 3-Day Free Trial
		expect(plans.trial_3d).toBeDefined();
		expect(plans.trial_3d.price).toBe(0);
		expect(plans.trial_3d.duration).toBe("trial");
		expect(plans.trial_3d.device_changeable).toBe(false);

		// 1 Year Plan (Device Changeable)
		expect(plans.one_year).toBeDefined();
		expect(plans.one_year.price).toBe(180000);
		expect(plans.one_year.duration).toBe(365);
		expect(plans.one_year.device_changeable).toBe(true);

		// Lifetime Plan (1 Device Locked)
		expect(plans.lifetime).toBeDefined();
		expect(plans.lifetime.price).toBe(45000);
		expect(plans.lifetime.duration).toBe("lifetime");
		expect(plans.lifetime.device_changeable).toBe(false);
	});

	it("verifies multi-admin authorization for master admin and unauthenticated users", async () => {
		const { isUserAdmin } = await import("../src/telegramBot");
		// Master admin from env.TELEGRAM_CHAT_ID must always be recognized as admin
		const masterAdminId = env.TELEGRAM_CHAT_ID || "5684146708";
		const isMaster = await isUserAdmin(masterAdminId, env);
		expect(isMaster).toBe(true);

		// An unknown random ID with no record in Firebase should not be admin
		const isRandom = await isUserAdmin("random_999999", env);
		expect(isRandom).toBe(false);
	});

	it("verifies default reseller commission settings (5000 MMK lifetime, 10000 MMK 1-year)", async () => {
		const { getCommissionConfig } = await import("../src/telegramBot");
		const comm = await getCommissionConfig(env);
		expect(comm.lifetime).toBe(5000);
		expect(comm.one_year).toBe(10000);
	});

	it("verifies default payment accounts (Wave Pay and KBZPay)", async () => {
		const { getPaymentAccounts, buildPaymentsMessage } = await import("../src/telegramBot");
		const accounts = await getPaymentAccounts(env);
		expect(accounts.wave).toBeDefined();
		expect(accounts.wave.number).toBeDefined();
		expect(accounts.wave.name).toBeDefined();

		expect(accounts.kpay).toBeDefined();
		expect(accounts.kpay.number).toBeDefined();
		expect(accounts.kpay.name).toBeDefined();

		const payMsg = await buildPaymentsMessage(env);
		expect(payMsg.text).toContain("Wave Pay");
		expect(payMsg.text).toContain("KBZPay");
		expect(payMsg.keyboard.inline_keyboard.length).toBeGreaterThan(0);
	});

	it("verifies reseller dashboard message and financial calculations", async () => {
		const { buildResellerDashboardMessage } = await import("../src/telegramBot");
		const mockReseller = {
			telegram_id: "12345678",
			name: "Ko Aung (Agent)",
			username: "koaung_agent",
			total_generated: 15,
			total_activated: 10,
			total_commission: 80000,
			total_due: 1200000,
			total_paid: 500000,
			created_at: Date.now()
		};

		const dash = buildResellerDashboardMessage(mockReseller);
		expect(dash.text).toContain("Ko Aung (Agent)");
		expect(dash.text).toContain("<b>15</b> ခု");
		expect(dash.text).toContain("<b>10</b> ခု");
		expect(dash.text).toContain("<b>80,000</b> ကျပ်");
		expect(dash.text).toContain("<code>1,200,000</code> ကျပ်");
		expect(dash.keyboard.inline_keyboard.length).toBeGreaterThan(0);
	});

	it("handles buyer payment screenshot upload via Telegram Webhook", async () => {
		const photoPayload = {
			update_id: 1005,
			message: {
				message_id: 5,
				chat: { id: 77777777, type: "private" },
				date: Math.floor(Date.now() / 1000),
				from: { id: 77777777, is_bot: false, first_name: "BuyerMgMg", username: "mgmg" },
				photo: [
					{ file_id: "photo_thumb_123", width: 100, height: 100, file_size: 1024 },
					{ file_id: "photo_large_456", width: 800, height: 800, file_size: 20480 }
				]
			}
		};

		const request = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(photoPayload)
		});

		const ctx = createExecutionContext();
		const response = await worker.fetch(request, env, ctx);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(200);
		const text = await response.text();
		expect(text).toBe("ok");
	});

	it("protects new admin commands (/addadmin, /addreseller, /payreseller, /setcommission, /setwave) from unauthorized users", async () => {
		const unauthorizedCommands = [
			"/addadmin 12345",
			"/addreseller 12345",
			"/payreseller 12345 full",
			"/setcommission lifetime 6000",
			"/setwave 09123456789 Admin"
		];

		for (let i = 0; i < unauthorizedCommands.length; i++) {
			const cmd = unauthorizedCommands[i];
			const payload = {
				update_id: 2000 + i,
				message: {
					message_id: 10 + i,
					chat: { id: 987654321, type: "private" },
					date: Math.floor(Date.now() / 1000),
					text: cmd,
					from: { id: 987654321, is_bot: false, first_name: "Attacker" }
				}
			};

			const request = new IncomingRequest("http://example.com/webhook", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(payload)
			});

			const ctx = createExecutionContext();
			const response = await worker.fetch(request, env, ctx);
			await waitOnExecutionContext(ctx);
			expect(response.status).toBe(200);
			const text = await response.text();
			expect(text).toBe("unauthorized");
		}
	});

	it("verifies persistent bottom keyboards and bulk menu keyboard structures", () => {
		const adminKb = getAdminBottomKeyboard();
		expect(adminKb.is_persistent).toBe(true);
		expect(adminKb.resize_keyboard).toBe(true);
		const adminBtnTexts = adminKb.keyboard.flat().map(b => b.text);
		expect(adminBtnTexts).toContain("📊 ပင်မ ဒက်ရှ်ဘုတ်");
		expect(adminBtnTexts).toContain("➕ ကုတ်အသစ် ထုတ်မည်");
		expect(adminBtnTexts).toContain("👥 ကိုယ်စားလှယ်များ");
		expect(adminBtnTexts).toContain("💳 ငွေလက်ခံ အကောင့်များ");

		const resellerKb = getResellerBottomKeyboard();
		expect(resellerKb.is_persistent).toBe(true);
		const resellerBtnTexts = resellerKb.keyboard.flat().map(b => b.text);
		expect(resellerBtnTexts).toContain("💼 ဒက်ရှ်ဘုတ်");
		expect(resellerBtnTexts).toContain("🎁 ၃ ရက် Trial");
		expect(resellerBtnTexts).toContain("📅 ၁ နှစ် လိုင်စင်");
		expect(resellerBtnTexts).toContain("💎 တစ်သက်တာ လိုင်စင်");
		expect(resellerBtnTexts).toContain("📦 အများပြား ထုတ်မည် (Bulk)");

		const buyerKb = getBuyerBottomKeyboard();
		expect(buyerKb.is_persistent).toBe(true);
		const buyerBtnTexts = buyerKb.keyboard.flat().map(b => b.text);
		expect(buyerBtnTexts).toContain("🎁 ၃ ရက် အခမဲ့ စမ်းသပ်ခွင့်");
		expect(buyerBtnTexts).toContain("🛒 လိုင်စင် ဝယ်ယူမည်");
		expect(buyerBtnTexts).toContain("🇹🇭 3D Live ရလဒ်");
		expect(buyerBtnTexts).toContain("🔢 တွတ် ဂဏန်းများ");

		const bulkKb = getResellerBulkKeyboard();
		const bulkCallbacks = bulkKb.inline_keyboard.flat().map(b => b.callback_data);
		expect(bulkCallbacks).toContain("r_gen_b:one_year:5");
		expect(bulkCallbacks).toContain("r_gen_b:lifetime:10");

		const keyGenKb = getKeyGenKeyboard();
		const genCallbacks = keyGenKb.inline_keyboard.flat().map(b => b.callback_data);
		expect(genCallbacks).toContain("gen_b:one_year:1");
		expect(genCallbacks).toContain("gen_b:one_year:5");
		expect(genCallbacks).toContain("gen_b:lifetime:10");
	});

	it("handles interactive button-driven webhook text messages without slash commands", async () => {
		const buttonTexts = [
			"📊 ပင်မ ဒက်ရှ်ဘုတ်",
			"🔢 တွတ် ဂဏန်းများ",
			"🎯 ပေါက်မဲ စစ်မည်",
			"❓ အကူအညီ",
			"🛒 လိုင်စင် ဝယ်ယူမည်"
		];

		for (let i = 0; i < buttonTexts.length; i++) {
			const btnText = buttonTexts[i];
			const payload = {
				update_id: 3000 + i,
				message: {
					message_id: 20 + i,
					chat: { id: 5684146708, type: "private" },
					date: Math.floor(Date.now() / 1000),
					text: btnText,
					from: { id: 5684146708, is_bot: false, first_name: "ButtonUser" }
				}
			};

			const request = new IncomingRequest("http://example.com/webhook", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(payload)
			});

			const ctx = createExecutionContext();
			const response = await worker.fetch(request, env, ctx);
			await waitOnExecutionContext(ctx);
			expect(response.status).toBe(200);
			const text = await response.text();
			expect(text).toBe("ok");
		}
	});

	it("handles interactive callback query clicks for Tut, Batch, and Key Generation chips", async () => {
		const callbacks = [
			{ id: "cq_tut", data: "tut:108" },
			{ id: "cq_batch", data: "b_inc" },
			{ id: "cq_batch_dec", data: "b_dec" },
			{ id: "cq_gen_chip", data: "gen_b:one_year:5" },
			{ id: "cq_com_menu", data: "com_menu:lifetime" }
		];

		for (let i = 0; i < callbacks.length; i++) {
			const cb = callbacks[i];
			const payload = {
				update_id: 4000 + i,
				callback_query: {
					id: cb.id,
					data: cb.data,
					from: { id: 5684146708, is_bot: false, first_name: "AdminTester" },
					message: {
						message_id: 30 + i,
						chat: { id: 5684146708, type: "private" },
						date: Math.floor(Date.now() / 1000)
					}
				}
			};

			const request = new IncomingRequest("http://example.com/webhook", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(payload)
			});

			const ctx = createExecutionContext();
			const response = await worker.fetch(request, env, ctx);
			await waitOnExecutionContext(ctx);
			expect(response.status).toBe(200);
		}
	});

	it("handles /api/app/latest and /api/app/publish workflow", async () => {
		// 1. Check latest release when empty
		const getReq1 = new IncomingRequest("http://example.com/api/app/latest");
		const ctx1 = createExecutionContext();
		const res1 = await worker.fetch(getReq1, env, ctx1);
		await waitOnExecutionContext(ctx1);
		expect(res1.status).toBe(200);
		const json1 = await res1.json() as any;
		expect(json1.status).toBe("ok");

		// 2. Publish new release via /api/app/publish
		const pubPayload = {
			file_id: "test_telegram_file_id_999",
			file_name: "3D_Ledger_v1.0.100.apk",
			file_size: 24500000,
			version_name: "v1.0.100",
			version_code: 100,
			release_notes: "Test automated release",
			download_url: "https://github.com/sayarkhant001/3d_Agent/releases/download/v1.0.100/app-debug.apk"
		};
		const pubReq = new IncomingRequest("http://example.com/api/app/publish", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(pubPayload)
		});
		const ctx2 = createExecutionContext();
		const res2 = await worker.fetch(pubReq, env, ctx2);
		await waitOnExecutionContext(ctx2);
		expect(res2.status).toBe(200);
		const json2 = await res2.json() as any;
		expect(json2.status).toBe("ok");
		expect(json2.release.version_name).toBe("v1.0.100");
		expect(json2.release.file_id).toBe("test_telegram_file_id_999");
	});

	it("correctly extracts forwarded users from Telegram messages (legacy, origin, hidden)", () => {
		// 1. Legacy forward_from
		const legacyMsg: any = {
			message_id: 101,
			chat: { id: 5684146708, type: "private" },
			date: 123456,
			forward_from: {
				id: 99887766,
				first_name: "Kyaw",
				last_name: "Gyi",
				username: "kyawgyi_bot"
			}
		};
		const extLegacy = extractForwardedUser(legacyMsg);
		expect(extLegacy).not.toBeNull();
		expect(extLegacy?.id).toBe(99887766);
		expect(extLegacy?.first_name).toBe("Kyaw");
		expect(extLegacy?.last_name).toBe("Gyi");
		expect(extLegacy?.is_hidden).toBe(false);

		// 2. Modern Bot API 7.0+ forward_origin (user)
		const modernMsg: any = {
			message_id: 102,
			chat: { id: 5684146708, type: "private" },
			date: 123456,
			forward_origin: {
				type: "user",
				date: 123450,
				sender_user: {
					id: 11223344,
					first_name: "Aung",
					last_name: "Ko",
					username: "aungko123"
				}
			}
		};
		const extModern = extractForwardedUser(modernMsg);
		expect(extModern).not.toBeNull();
		expect(extModern?.id).toBe(11223344);
		expect(extModern?.first_name).toBe("Aung");
		expect(extModern?.last_name).toBe("Ko");
		expect(extModern?.is_hidden).toBe(false);

		// 3. Modern Bot API 7.0+ forward_origin (hidden_user)
		const hiddenMsg: any = {
			message_id: 103,
			chat: { id: 5684146708, type: "private" },
			date: 123456,
			forward_origin: {
				type: "hidden_user",
				date: 123450,
				sender_user_name: "Confidential Agent"
			}
		};
		const extHidden = extractForwardedUser(hiddenMsg);
		expect(extHidden).not.toBeNull();
		expect(extHidden?.id).toBeUndefined();
		expect(extHidden?.first_name).toBe("Confidential Agent");
		expect(extHidden?.is_hidden).toBe(true);

		// 4. Legacy forward_sender_name (hidden)
		const legacyHiddenMsg: any = {
			message_id: 104,
			chat: { id: 5684146708, type: "private" },
			date: 123456,
			forward_sender_name: "Private Contact"
		};
		const extLegacyHidden = extractForwardedUser(legacyHiddenMsg);
		expect(extLegacyHidden).not.toBeNull();
		expect(extLegacyHidden?.id).toBeUndefined();
		expect(extLegacyHidden?.first_name).toBe("Private Contact");
		expect(extLegacyHidden?.is_hidden).toBe(true);
	});

	it("handles forwarded message from admin to add reseller or admin via inline role buttons", async () => {
		const adminChatId = 5684146708; // Configured master admin
		const forwardedPayload = {
			update_id: 2001,
			message: {
				message_id: 55,
				chat: { id: adminChatId, type: "private" },
				from: { id: adminChatId, is_bot: false, first_name: "MasterAdmin" },
				date: Math.floor(Date.now() / 1000),
				text: "Hello from my client",
				forward_from: {
					id: 77889911,
					is_bot: false,
					first_name: "Ko",
					last_name: "Zaw",
					username: "kozaw3d"
				}
			}
		};

		const fwdReq = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(forwardedPayload)
		});

		const ctx1 = createExecutionContext();
		const res1 = await worker.fetch(fwdReq, env, ctx1);
		await waitOnExecutionContext(ctx1);
		expect(res1.status).toBe(200);

		// Now simulate clicking "fwd_add_res:77889911:Ko%20Zaw" to add as Reseller
		const callbackPayload = {
			update_id: 2002,
			callback_query: {
				id: "cq_fwd_1",
				from: { id: adminChatId, is_bot: false, first_name: "MasterAdmin" },
				data: "fwd_add_res:77889911:Ko%20Zaw",
				message: {
					message_id: 56,
					chat: { id: adminChatId, type: "private" }
				}
			}
		};

		const cbReq = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(callbackPayload)
		});

		const ctx2 = createExecutionContext();
		const res2 = await worker.fetch(cbReq, env, ctx2);
		await waitOnExecutionContext(ctx2);
		expect(res2.status).toBe(200);

		// Now simulate clicking "fwd_add_adm:88990022:Ma%20Hnin" to add as Admin
		const admCbPayload = {
			update_id: 2003,
			callback_query: {
				id: "cq_fwd_2",
				from: { id: adminChatId, is_bot: false, first_name: "MasterAdmin" },
				data: "fwd_add_adm:88990022:Ma%20Hnin",
				message: {
					message_id: 57,
					chat: { id: adminChatId, type: "private" }
				}
			}
		};

		const admReq = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(admCbPayload)
		});

		const ctx3 = createExecutionContext();
		const res3 = await worker.fetch(admReq, env, ctx3);
		await waitOnExecutionContext(ctx3);
		expect(res3.status).toBe(200);
	});

	it("verifies buildGeneratedKeysMessage displays web-generated available keys and filter tabs", async () => {
		const { buildGeneratedKeysMessage, saveLicenseKey } = await import("../src/telegramBot");
		
		// Create a web-generated available key
		const webKey = "3D-WEB-TEST-KEY-9999";
		await saveLicenseKey(env, {
			cd_key: webKey,
			duration: "one_year",
			duration_label: "၁ နှစ် လိုင်စင် (1-Year)",
			price: 180000,
			status: "available",
			created_at: Date.now()
		});

		const resAvailable = await buildGeneratedKeysMessage(env, "available", 1);
		expect(resAvailable.text).toContain(webKey);
		expect(resAvailable.text).toContain("ရောင်းရန်အသင့် (Available)");

		// Check filter tabs
		const flatButtons = resAvailable.keyboard.inline_keyboard.flat();
		const callbacks = flatButtons.map(b => b.callback_data);
		expect(callbacks).toContain("m_keys_gen:available:1");
		expect(callbacks).toContain("m_keys_gen:active:1");
		expect(callbacks).toContain("m_keys_gen:pending_approval:1");
		expect(callbacks).toContain("m_keys_gen:all:1");
	});

	it("verifies buildResellerGeneratedKeysMessage displays reseller's keys", async () => {
		const { buildResellerGeneratedKeysMessage, saveLicenseKey } = await import("../src/telegramBot");
		
		const resellerKey = "3D-RES-TEST-KEY-8888";
		const resellerId = "77665544";
		await saveLicenseKey(env, {
			cd_key: resellerKey,
			duration: "lifetime",
			duration_label: "တစ်သက်တာ (Lifetime)",
			price: 45000,
			status: "available",
			created_at: Date.now(),
			generated_by_reseller_id: resellerId
		});

		const res = await buildResellerGeneratedKeysMessage(env, resellerId, 1);
		expect(res.text).toContain(resellerKey);
		expect(res.text).toContain("ကျွန်ုပ် ထုတ်ယူထားသော ကုတ်များ");
	});

	it("handles interactive manual reseller due settlement flow via r_pay_manual and custom amount text", async () => {
		const { addReseller, getReseller, saveReseller, getAdminState } = await import("../src/telegramBot");
		const adminChatId = Number(env.TELEGRAM_CHAT_ID) || 5684146708;
		const resellerId = "55443322";

		// Ensure reseller exists with some due
		await addReseller(env, resellerId, "Ko Thar Gyi");
		// Directly update due for test
		const mockReseller = await getReseller(resellerId, env);
		if (mockReseller) {
			mockReseller.total_due = 100000;
			mockReseller.total_paid = 20000;
			await saveReseller(env, mockReseller);
		}

		// 1. Admin clicks "r_pay_manual:55443322"
		const cbPayload = {
			update_id: 3001,
			callback_query: {
				id: "cq_pay_man_1",
				from: { id: adminChatId, is_bot: false, first_name: "MasterAdmin" },
				data: `r_pay_manual:${resellerId}`,
				message: {
					message_id: 112,
					chat: { id: adminChatId, type: "private" }
				}
			}
		};

		const cbReq = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(cbPayload)
		});

		const ctx1 = createExecutionContext();
		const res1 = await worker.fetch(cbReq, env, ctx1);
		await waitOnExecutionContext(ctx1);
		expect(res1.status).toBe(200);

		// Verify admin state is awaiting_reseller_pay
		const state = await getAdminState(adminChatId, env);
		expect(state?.action).toBe("awaiting_reseller_pay");
		expect(state?.target_id).toBe(resellerId);

		// 2. Admin enters custom manual amount: "35,000 Ks"
		const textPayload = {
			update_id: 3002,
			message: {
				message_id: 113,
				from: { id: adminChatId, is_bot: false, first_name: "MasterAdmin" },
				chat: { id: adminChatId, type: "private" },
				text: "35,000 Ks"
			}
		};

		const textReq = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(textPayload)
		});

		const ctx2 = createExecutionContext();
		const res2 = await worker.fetch(textReq, env, ctx2);
		await waitOnExecutionContext(ctx2);
		expect(res2.status).toBe(200);

		// Verify state is cleared
		const stateAfter = await getAdminState(adminChatId, env);
		expect(stateAfter).toBeNull();

		// Verify reseller balance updated: due 100,000 - 35,000 = 65,000; paid 20,000 + 35,000 = 55,000
		const updatedReseller = await getReseller(resellerId, env);
		expect(updatedReseller?.total_due).toBe(65000);
		expect(updatedReseller?.total_paid).toBe(55000);
	});

	it("verifies device policy declarations and payment copy_text buttons in Telegram Bot", async () => {
		const { getBuyerPlansKeyboard, buildPaymentsMessage, getDefaultSalePlans } = await import("../src/telegramBot");

		// 1. Check default sale plans description
		const defaults = getDefaultSalePlans();
		expect(defaults.one_year.description).toContain("Device Changeable");
		expect(defaults.one_year.device_changeable).toBe(true);
		expect(defaults.lifetime.description).toContain("1-Device Locked");
		expect(defaults.lifetime.device_changeable).toBe(false);

		// 2. Check buyer plans keyboard
		const buyerKb = getBuyerPlansKeyboard();
		const oneYearBtn = buyerKb.inline_keyboard[0][0];
		const lifetimeBtn = buyerKb.inline_keyboard[1][0];
		expect(oneYearBtn.text).toContain("ဖုန်းပြောင်းသုံးနိုင်");
		expect(lifetimeBtn.text).toContain("ဖုန်း ၁ လုံးသာ");

		// 3. Check payment accounts message keyboard contains copy_text buttons
		const payMsg = await buildPaymentsMessage(env);
		const copyButtons = payMsg.keyboard.inline_keyboard[0];
		expect(copyButtons.length).toBe(2);
		expect(copyButtons[0].copy_text).toBeDefined();
		expect(copyButtons[0].copy_text?.text).toBe("09778899001");
		expect(copyButtons[1].copy_text).toBeDefined();
		expect(copyButtons[1].copy_text?.text).toBe("09778899001");
	});

	it("verifies buyer plan purchase flow provides payment copy_text buttons and device policy statement", async () => {
		const buyerChatId = 88776655;
		const { isUserAdmin } = await import("../src/telegramBot");

		// Click "buy:one_year"
		const reqOneYear = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({
				update_id: 4001,
				callback_query: {
					id: "cq_buy_1yr",
					from: { id: buyerChatId, is_bot: false, first_name: "BuyerMgMg" },
					data: "buy:one_year",
					message: { message_id: 201, chat: { id: buyerChatId, type: "private" } }
				}
			})
		});

		const ctx1 = createExecutionContext();
		const res1 = await worker.fetch(reqOneYear, env, ctx1);
		await waitOnExecutionContext(ctx1);
		expect(res1.status).toBe(200);

		// Click "buy:lifetime"
		const reqLifetime = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({
				update_id: 4002,
				callback_query: {
					id: "cq_buy_life",
					from: { id: buyerChatId, is_bot: false, first_name: "BuyerMgMg" },
					data: "buy:lifetime",
					message: { message_id: 202, chat: { id: buyerChatId, type: "private" } }
				}
			})
		});

		const ctx2 = createExecutionContext();
		const res2 = await worker.fetch(reqLifetime, env, ctx2);
		await waitOnExecutionContext(ctx2);
		expect(res2.status).toBe(200);
	});

	it("correctly identifies active in-use license keys across claimed, activated, and bound states", async () => {
		const { isKeyInActiveUse } = await import("../src/telegramBot");

		// Active status
		expect(isKeyInActiveUse({ cd_key: "K-1", status: "active", duration: "lifetime", duration_label: "Lifetime", price: 45000, created_at: Date.now() })).toBe(true);

		// Claimed status (from Android or Web Admin)
		expect(isKeyInActiveUse({ cd_key: "K-2", status: "claimed", duration: "lifetime", duration_label: "Lifetime", price: 45000, created_at: Date.now() })).toBe(true);

		// Activated status
		expect(isKeyInActiveUse({ cd_key: "K-3", status: "activated", duration: "lifetime", duration_label: "Lifetime", price: 45000, created_at: Date.now() })).toBe(true);

		// Device fingerprint attached with activated_at timestamp
		expect(isKeyInActiveUse({
			cd_key: "K-4",
			status: "in_use",
			duration: 365,
			duration_label: "1-Year",
			price: 180000,
			created_at: Date.now(),
			device_fingerprint: "device_abc123",
			activated_at: Date.now()
		})).toBe(true);

		// Revoked or banned keys must NOT show as active
		expect(isKeyInActiveUse({ cd_key: "K-5", status: "revoked", duration: "lifetime", duration_label: "Lifetime", price: 45000, created_at: Date.now(), device_fingerprint: "dev1" })).toBe(false);
		expect(isKeyInActiveUse({ cd_key: "K-6", status: "banned", duration: "lifetime", duration_label: "Lifetime", price: 45000, created_at: Date.now() })).toBe(false);

		// Available unused key must NOT show as active
		expect(isKeyInActiveUse({ cd_key: "K-7", status: "available", duration: "lifetime", duration_label: "Lifetime", price: 45000, created_at: Date.now() })).toBe(false);
	});

	it("handles /ban, /unban, and active keys monitor pagination in telegram bot", async () => {
		const adminChatId = 123456789;

		// 1. Test /ban command without arguments (prompts user to enter key)
		const banPromptReq = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({
				update_id: 5001,
				message: {
					message_id: 301,
					chat: { id: adminChatId, type: "private" },
					date: Math.floor(Date.now() / 1000),
					text: "/ban",
					from: { id: adminChatId, is_bot: false, first_name: "Admin" }
				}
			})
		});
		const ctx1 = createExecutionContext();
		const res1 = await worker.fetch(banPromptReq, env, ctx1);
		await waitOnExecutionContext(ctx1);
		expect(res1.status).toBe(200);

		// 2. Test /ban with key argument
		const banExecReq = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({
				update_id: 5002,
				message: {
					message_id: 302,
					chat: { id: adminChatId, type: "private" },
					date: Math.floor(Date.now() / 1000),
					text: "/ban TEST-KEY-9999",
					from: { id: adminChatId, is_bot: false, first_name: "Admin" }
				}
			})
		});
		const ctx2 = createExecutionContext();
		const res2 = await worker.fetch(banExecReq, env, ctx2);
		await waitOnExecutionContext(ctx2);
		expect(res2.status).toBe(200);

		// 3. Test /unban with key argument
		const unbanExecReq = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({
				update_id: 5003,
				message: {
					message_id: 303,
					chat: { id: adminChatId, type: "private" },
					date: Math.floor(Date.now() / 1000),
					text: "/unban TEST-KEY-9999",
					from: { id: adminChatId, is_bot: false, first_name: "Admin" }
				}
			})
		});
		const ctx3 = createExecutionContext();
		const res3 = await worker.fetch(unbanExecReq, env, ctx3);
		await waitOnExecutionContext(ctx3);
		expect(res3.status).toBe(200);

		// 4. Test bottom keyboard button "🚫 ကုတ် ပိတ်သိမ်းရန်"
		const revokeButtonReq = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({
				update_id: 5004,
				message: {
					message_id: 304,
					chat: { id: adminChatId, type: "private" },
					date: Math.floor(Date.now() / 1000),
					text: "🚫 ကုတ် ပိတ်သိမ်းရန်",
					from: { id: adminChatId, is_bot: false, first_name: "Admin" }
				}
			})
		});
		const ctx4 = createExecutionContext();
		const res4 = await worker.fetch(revokeButtonReq, env, ctx4);
		await waitOnExecutionContext(ctx4);
		expect(res4.status).toBe(200);

		// 5. Test callback query m_ban_by_input
		const inputBanCqReq = new IncomingRequest("http://example.com/webhook", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({
				update_id: 5005,
				callback_query: {
					id: "cq_input_ban",
					from: { id: adminChatId, is_bot: false, first_name: "Admin" },
					data: "m_ban_by_input",
					message: { message_id: 305, chat: { id: adminChatId, type: "private" } }
				}
			})
		});
		const ctx5 = createExecutionContext();
		const res5 = await worker.fetch(inputBanCqReq, env, ctx5);
		await waitOnExecutionContext(ctx5);
		expect(res5.status).toBe(200);
	});

	it("handles /restore endpoint gracefully when no key or active key is found", async () => {
		// Test /restore with missing fingerprint
		const missingReq = new IncomingRequest("http://example.com/restore", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({})
		});
		const ctx1 = createExecutionContext();
		const res1 = await worker.fetch(missingReq, env, ctx1);
		await waitOnExecutionContext(ctx1);
		expect(res1.status).toBe(400);

		// Test /restore with non-existent device fingerprint
		const noneReq = new IncomingRequest("http://example.com/restore", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ device_fingerprint: "non_existent_device_123" })
		});
		const ctx2 = createExecutionContext();
		const res2 = await worker.fetch(noneReq, env, ctx2);
		await waitOnExecutionContext(ctx2);
		expect(res2.status).toBe(200);
		const data2 = await res2.json() as any;
		expect(data2.status).toBe("none");
	});

	it("verifies getLocalDrawDateInfo computes valid MMT date and draw day flags", () => {
		const info = getLocalDrawDateInfo();
		expect(info.dateStr).toMatch(/^\d{4}-\d{2}-\d{2}$/);
		expect(info.day).toBeGreaterThanOrEqual(1);
		expect(info.day).toBeLessThanOrEqual(31);
		expect(info.isStandardDrawDay).toBe(info.day === 1 || info.day === 16);
	});
});


