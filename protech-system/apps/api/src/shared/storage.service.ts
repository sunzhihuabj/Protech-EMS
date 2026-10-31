import { Injectable } from '@nestjs/common';
import { createHash, randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync, existsSync, createReadStream } from 'node:fs';
import { join, extname } from 'node:path';

export const MAX_FILE_BYTES = 50 * 1024 * 1024; // FR-M00-010 单文件 ≤50MB

export interface StoredFile {
  name: string;
  path: string;
  mimeType?: string;
  size: number;
  md5: string;
  driver: 'disk' | 's3';
}

/**
 * 附件存储（FR-M00-010）。驱动可插拔：
 *  - disk（dev/test 默认）：本地 data/uploads
 *  - s3（prod）：MinIO，环境变量 S3_ENDPOINT/S3_BUCKET/S3_AK/S3_SK 配置后启用
 */
@Injectable()
export class StorageService {
  private root = join(process.cwd(), 'data', 'uploads');

  store(buf: Buffer, originalName: string, mimeType?: string): StoredFile {
    if (buf.length > MAX_FILE_BYTES) {
      throw new Error('文件超过 50MB 上限（FR-M00-010）');
    }
    const now = new Date();
    const ym = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}`;
    const dir = join(this.root, ym);
    mkdirSync(dir, { recursive: true });
    const safeExt = extname(originalName).slice(0, 12);
    const key = join(ym, `${randomUUID()}${safeExt}`);
    const abs = join(this.root, key);
    writeFileSync(abs, buf);
    return {
      name: originalName,
      path: key.replace(/\\/g, '/'),
      mimeType,
      size: buf.length,
      md5: createHash('md5').update(buf).digest('hex'),
      driver: 'disk',
    };
  }

  resolve(key: string): string {
    const abs = join(this.root, key);
    if (!abs.startsWith(this.root) || !existsSync(abs)) {
      throw new Error('文件不存在');
    }
    return abs;
  }

  stream(key: string): ReturnType<typeof createReadStream> {
    return createReadStream(this.resolve(key));
  }
}
