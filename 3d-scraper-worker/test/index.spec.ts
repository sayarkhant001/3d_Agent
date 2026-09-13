import {
	env,
	createExecutionContext,
	waitOnExecutionContext,
	SELF,
} from "cloudflare:test";
import { describe, it, expect } from "vitest";
import worker, { calculateNextThaiDrawDate } from "../src/index";

const IncomingRequest = Request<unknown, IncomingRequestCfProperties>;

describe("3D Scraper GLO Worker", () => {
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

	it("calculates next Thai lottery draw dates accurately", () => {
		// Draw on 1st -> next is 16th of same month
		expect(calculateNextThaiDrawDate("2026-09-01")).toBe("2026-09-16");
		// Draw on 16th -> next is 1st of next month
		expect(calculateNextThaiDrawDate("2026-09-16")).toBe("2026-10-01");
		// Draw on Dec 16th -> next is Jan 1st of next year
		expect(calculateNextThaiDrawDate("2026-12-16")).toBe("2027-01-01");
	});
});
