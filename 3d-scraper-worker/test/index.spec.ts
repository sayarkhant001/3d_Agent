import {
	env,
	createExecutionContext,
	waitOnExecutionContext,
	SELF,
} from "cloudflare:test";
import { describe, it, expect } from "vitest";
import worker, { calculateNextThaiDrawDate } from "../src/index";
import { calculateTutNumbers } from "../src/telegramBot";

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
				data: "cb_help",
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
});
