import {
	env,
	createExecutionContext,
	waitOnExecutionContext,
	SELF,
} from "cloudflare:test";
import { describe, it, expect } from "vitest";
import worker from "../src/index";

// For now, you'll need to do something like this to get a correctly-typed
// `Request` to pass to `worker.fetch()`.
const IncomingRequest = Request<unknown, IncomingRequestCfProperties>;

describe("3D License API Worker", () => {
	it("responds with Method Not Allowed on GET (unit style)", async () => {
		const request = new IncomingRequest("http://example.com");
		const ctx = createExecutionContext();
		const response = await worker.fetch(request, env, ctx);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(405);
		const json = await response.json() as { error: string };
		expect(json.error).toBe("Method Not Allowed");
	});

	it("responds with CORS headers on OPTIONS", async () => {
		const request = new IncomingRequest("http://example.com", { method: "OPTIONS" });
		const ctx = createExecutionContext();
		const response = await worker.fetch(request, env, ctx);
		await waitOnExecutionContext(ctx);
		expect(response.headers.get("Access-Control-Allow-Origin")).toBe("*");
	});
});
