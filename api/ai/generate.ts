import type { VercelRequest, VercelResponse } from "@vercel/node";

const GEMINI_MODEL = "gemini-2.5-flash";
const GEMINI_URL = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;

function sendError(res: VercelResponse, status: number, error: string) {
  return res.status(status).json({ success: false, error });
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return sendError(res, 405, "Method not allowed");
  }

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return sendError(res, 500, "AI service is not configured");
  }

  const content = typeof req.body?.content === "string" ? req.body.content : "";
  if (!content.trim()) {
    return sendError(res, 400, "Content must not be empty");
  }

  try {
    const response = await fetch(`${GEMINI_URL}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": apiKey,
      },
      body: JSON.stringify({
        contents: [
          {
            role: "user",
            parts: [{ text: content }],
          },
        ],
      }),
      signal: AbortSignal.timeout(30_000),
    });

    if (!response.ok) {
      return sendError(res, response.status >= 500 ? 502 : response.status, "Unable to generate an AI response");
    }

    const data = await response.json() as {
      candidates?: Array<{
        content?: {
          parts?: Array<{ text?: string }>;
        };
      }>;
    };

    const text = data.candidates
      ?.flatMap((candidate) => candidate.content?.parts ?? [])
      .map((part) => part.text ?? "")
      .join("")
      .trim();

    if (!text) {
      return sendError(res, 502, "AI returned an empty response");
    }

    return res.status(200).json({ success: true, text });
  } catch {
    return sendError(res, 502, "Unable to generate an AI response");
  }
}
